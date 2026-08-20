#!/usr/bin/env python3
"""Black-box checks for the staged stdio MCP example.

Each test starts a fresh subprocess of the staged launcher and validates raw
JSON-RPC wire behaviour with the standard library only. No Scala encoders are
involved: every payload is a literal Python dict or UTF-8 byte string.

Usage:
    python3 -B scripts/test_stdio.py --launcher target/stdio-example/bin/mcp-stdio-example
"""

import argparse
import contextlib
import json
import os
import select
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import zipfile
from pathlib import Path

PROTOCOL_VERSION = "2026-07-28"
META = {
    "io.modelcontextprotocol/protocolVersion": PROTOCOL_VERSION,
    "io.modelcontextprotocol/clientCapabilities": {},
}
SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo"
SERVER_NAME = "mcp-scala-sdk-example-stdio"
DEADLINE = 10.0
PROCESS_LOG = os.environ.get(
    "STDIO_PROCESS_LOG", "/tmp/mcp-alpha-process-subprocesses.log"
)


class LineReader:
    """Deadline-bounded line reader on a pipe file descriptor."""

    def __init__(self, fd):
        self.fd = fd
        self.buffer = bytearray()

    def readline(self, timeout=DEADLINE):
        end = time.monotonic() + timeout
        while b"\n" not in self.buffer:
            remaining = end - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("no output line within deadline")
            ready, _, _ = select.select([self.fd], [], [], remaining)
            if not ready:
                raise TimeoutError("no output line within deadline")
            chunk = os.read(self.fd, 65536)
            if not chunk:  # EOF
                if self.buffer:
                    self.buffer = bytearray()
                    raise ValueError("unterminated stdout frame")
                return None
            self.buffer += chunk
        line, self.buffer = self.buffer.split(b"\n", 1)
        return bytes(line)


def write_all(proc, data, timeout=DEADLINE):
    """Bound the stdin write so a blocked write cannot defeat read timeouts."""
    errors = []

    def send():
        try:
            view = memoryview(data)
            while len(view) > 0:
                written = proc.stdin.write(view)
                if written is None:
                    written = 0
                if written == 0:
                    raise OSError("stdin write made no progress")
                view = view[written:]
            proc.stdin.flush()
        except (OSError, ValueError) as exc:  # propagate to caller thread
            errors.append(exc)

    worker = threading.Thread(target=send, daemon=True)
    worker.start()
    worker.join(timeout)
    if worker.is_alive():
        raise TimeoutError("stdin write blocked past deadline")
    if errors:
        raise errors[0]


class StdioServer:
    """Owns one example subprocess plus its stderr drain."""

    def __init__(self, argv, env=None, cwd=None, label=""):
        self.argv = argv
        self.label = label or argv[0]
        self.proc = subprocess.Popen(
            argv,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
            env=env,
            cwd=cwd,
        )
        self.stderr_chunks = []
        self.drain_error = None
        self._tearing_down = False
        self.closed = False
        self._stderr_thread = threading.Thread(target=self._drain_stderr, daemon=True)
        self._stderr_thread.start()
        self.stdout = LineReader(self.proc.stdout.fileno())

    def _drain_stderr(self):
        try:
            while True:
                chunk = os.read(self.proc.stderr.fileno(), 65536)
                if not chunk:
                    return
                self.stderr_chunks.append(chunk)
        except (OSError, ValueError) as exc:
            if not self._tearing_down:
                self.drain_error = exc

    def send(self, message):
        payload = (
            json.dumps(message, separators=(",", ":"), ensure_ascii=False) + "\n"
        ).encode("utf-8")
        write_all(self.proc, payload)

    def send_raw(self, data):
        write_all(self.proc, data)

    def read_line(self, timeout=DEADLINE):
        return self.stdout.readline(timeout)

    def read_json(self, timeout=DEADLINE):
        line = self.read_line(timeout)
        assert line is not None, "process closed stdout before replying"
        return json.loads(line)

    def close_stdin(self):
        self.proc.stdin.close()

    def wait(self, timeout=DEADLINE):
        return self.proc.wait(timeout=timeout)

    def stderr_text(self):
        self._stderr_thread.join(timeout=2)
        if self.drain_error is not None:
            raise RuntimeError("stderr drain failed")
        return b"".join(self.stderr_chunks).decode("utf-8", "replace")

    def stop(self):
        if self.closed:
            return
        self.closed = True
        self._tearing_down = True
        rc = self.proc.poll()
        unreaped = False
        if rc is None:
            self.proc.kill()
            try:
                rc = self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                unreaped = True
                rc = "unreaped"
        self._stderr_thread.join(timeout=2)
        for stream in (self.proc.stdin, self.proc.stdout, self.proc.stderr):
            with contextlib.suppress(OSError, ValueError):  # cleanup may double-close
                stream.close()
        record = (
            f"--- label={self.label} argv={self.argv} rc={rc}\n"
            f"stderr: {b''.join(self.stderr_chunks).decode('utf-8', 'replace')!r}\n"
        )
        try:
            with open(PROCESS_LOG, "a", encoding="utf-8") as handle:
                handle.write(record)
        except OSError:
            print("warning: could not write stdio process log", file=sys.stderr)
        if unreaped:
            raise RuntimeError(
                f"could not reap subprocess {self.proc.pid} ({self.label})"
            )

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        try:
            if exc_type is None:
                with contextlib.suppress(OSError, ValueError):
                    self.close_stdin()
                rc = self.wait(timeout=DEADLINE)
                if rc != 0:
                    raise AssertionError(
                        f"{self.label} exited with rc={rc}, stderr={self.stderr_text()!r}"
                    )
                self.stderr_text()
                leftover = self.read_line(timeout=1)
                if leftover is not None:
                    raise AssertionError(
                        f"{self.label} produced unexpected extra reply: {leftover!r}"
                    )
        finally:
            self.stop()
        return False


def rpc(method, request_id, params=None):
    message = {"jsonrpc": "2.0", "method": method, "id": request_id}
    if params is not None:
        message["params"] = params
    return message


def ping_request(request_id, arguments=None):
    params = {"name": "ping", "_meta": META}
    if arguments is not None:
        params["arguments"] = arguments
    return rpc("tools/call", request_id, params)


def manifest_version(jar_path):
    """Read Implementation-Version from the bootstrap jar manifest."""
    with zipfile.ZipFile(jar_path) as archive:
        raw = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    attributes = {}
    key = None
    for line in raw.split("\r\n"):
        if line.startswith(" ") and key is not None:
            attributes[key] += line[1:].strip()
        elif ":" in line:
            key, _, value = line.partition(":")
            key = key.strip()
            attributes[key] = value.strip()
        else:
            key = None
    return attributes["Implementation-Version"]


def staged_root(launcher):
    return Path(launcher).resolve().parent.parent


def bootstrap_jar(launcher):
    return staged_root(launcher) / "mcp-stdio-example.jar"


class StdioExampleSuite(unittest.TestCase):
    launcher = None
    expected_version = None

    def assert_success(self, reply, request_id):
        self.assertEqual(reply["jsonrpc"], "2.0")
        self.assertIn("result", reply)
        self.assertNotIn("error", reply)
        self.assertEqual(reply["id"], request_id)
        return reply["result"]

    def assert_error(self, reply, code, request_id="__absent__"):
        self.assertEqual(reply["jsonrpc"], "2.0")
        self.assertIn("error", reply)
        self.assertNotIn("result", reply)
        self.assertEqual(reply["error"]["code"], code)
        self.assertIsInstance(reply["error"]["message"], str)
        if request_id == "__absent__":
            self.assertNotIn("id", reply)
        else:
            self.assertEqual(reply["id"], request_id)
        return reply["error"]

    def assert_pong(self, reply, request_id):
        result = self.assert_success(reply, request_id)
        self.assertEqual(result["resultType"], "complete")
        self.assertEqual(result["content"][0]["type"], "text")
        self.assertEqual(result["content"][0]["text"], "pong")
        identity = result["_meta"][SERVER_INFO_KEY]
        self.assertEqual(identity["name"], SERVER_NAME)
        self.assertEqual(identity["version"], self.expected_version)
        return result

    def test_discovery_tools_and_ping_one_process(self):
        with StdioServer([self.launcher], label="discovery") as server:
            server.send(rpc("server/discover", "d1", {"_meta": META}))
            server.send(rpc("tools/list", "d2", {"_meta": META}))
            server.send(ping_request("d3"))
            discover = server.read_json()
            listing = server.read_json()
            pong = server.read_json()

        result = self.assert_success(discover, "d1")
        self.assertEqual(result["supportedVersions"], [PROTOCOL_VERSION])
        self.assertIn("tools", result["capabilities"])
        self.assertEqual(result["resultType"], "complete")
        self.assertEqual(result["cacheScope"], "public")
        identity = result["_meta"][SERVER_INFO_KEY]
        self.assertEqual(identity["name"], SERVER_NAME)
        self.assertEqual(identity["version"], self.expected_version)

        listing_result = self.assert_success(listing, "d2")
        tools = listing_result["tools"]
        self.assertEqual(len(tools), 1)
        self.assertEqual(tools[0]["name"], "ping")
        self.assertEqual(tools[0]["inputSchema"]["type"], "object")
        self.assertEqual(tools[0]["inputSchema"]["additionalProperties"], False)

        self.assert_pong(pong, "d3")

    def test_invalid_json_then_valid_ping(self):
        with StdioServer([self.launcher], label="invalid-json") as server:
            server.send_raw(b'{"jsonrpc": broken secret-input\n')
            reply = server.read_json()
            self.assert_error(reply, -32700)
            self.assertNotIn("secret-input", json.dumps(reply))
            server.send(ping_request("after"))
            self.assert_pong(server.read_json(), "after")
        self.assertNotIn("secret-input", server.stderr_text())

    def test_invalid_envelopes_then_valid_request(self):
        bad_frames = [
            b'[{"jsonrpc":"2.0","method":"tools/call","id":1,"params":{}}]\n',
            rpc("tools/call", {"x": 1}, {"name": "ping", "_meta": META}),
            rpc("tools/call", None, {"name": "ping", "_meta": META}),
            rpc("tools/call", 1.5, {"name": "ping", "_meta": META}),
            rpc("tools/call", 9223372036854775808, {"name": "ping", "_meta": META}),
        ]
        with StdioServer([self.launcher], label="bad-envelopes") as server:
            for frame in bad_frames:
                if isinstance(frame, bytes):
                    server.send_raw(frame)
                else:
                    server.send(frame)
                reply = server.read_json()
                self.assert_error(reply, -32600)
            server.send(ping_request("after"))
            self.assert_pong(server.read_json(), "after")

    def test_params_rejections_carry_the_request_id(self):
        cases = [
            # Missing request metadata.
            rpc("server/discover", "p1", {}),
            # Malformed tool params: arguments must be an object.
            rpc(
                "tools/call", "p2", {"name": "ping", "arguments": "nope", "_meta": META}
            ),
            # Unknown tool name carrying a secret and a newline.
            rpc("tools/call", "p3", {"name": "secret-name\nleak", "_meta": META}),
            # Nonempty arguments against an empty-object ping schema.
            ping_request("p4", arguments={"surprise": 1}),
        ]
        with StdioServer([self.launcher], label="params-errors") as server:
            for index, message in enumerate(cases):
                server.send(message)
                reply = server.read_json()
                error = self.assert_error(reply, -32602, request_id=f"p{index + 1}")
                self.assertNotIn("secret-name", json.dumps(reply))
                self.assertNotIn("leak", json.dumps(reply))
                self.assertNotIn("surprise", json.dumps(reply))
                self.assertNotIn("nope", json.dumps(reply))
                if index == 2:
                    self.assertEqual(error["message"], "unknown tool")
            server.send(ping_request("after"))
            self.assert_pong(server.read_json(), "after")
        stderr = server.stderr_text()
        for marker in ("secret-name", "leak", "surprise", "nope"):
            self.assertNotIn(marker, stderr)

    def test_unsupported_protocol_version(self):
        request = rpc(
            "server/discover",
            "v1",
            {
                "_meta": dict(
                    META, **{"io.modelcontextprotocol/protocolVersion": "1999-01-01"}
                )
            },
        )
        with StdioServer([self.launcher], label="bad-version") as server:
            server.send(request)
            reply = server.read_json()
        error = self.assert_error(reply, -32022, request_id="v1")
        self.assertEqual(error["data"]["supported"], [PROTOCOL_VERSION])
        self.assertEqual(error["data"]["requested"], "1999-01-01")

    def test_unknown_method_with_valid_metadata(self):
        with StdioServer([self.launcher], label="unknown-method") as server:
            server.send(rpc("bogus/method", "u1", {"_meta": META}))
            reply = server.read_json()
        self.assert_error(reply, -32601, request_id="u1")

    def test_notifications_and_inbound_responses_stay_silent(self):
        frames = [
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "params": {"name": "ping", "_meta": META},
            },
            {"jsonrpc": "2.0", "method": "tools/call", "params": 123},
            {"jsonrpc": "2.0", "id": 5, "result": {}},
            {
                "jsonrpc": "2.0",
                "id": 6,
                "error": {"code": -32603, "message": "client side"},
            },
        ]
        with StdioServer([self.launcher], label="silent-inputs") as server:
            for frame in frames:
                server.send(frame)
            server.send(ping_request("only"))
            replies = [server.read_json()]
            self.assert_pong(replies[0], "only")

    def test_ids_crlf_and_unterminated_frame(self):
        ids = [9223372036854775807, -9223372036854775808, 'é "x"\\\nline']
        with StdioServer([self.launcher], label="ids-frames") as server:
            for request_id in ids:
                server.send(ping_request(request_id))
                self.assert_pong(server.read_json(), request_id)
            crlf = (json.dumps(ping_request("crlf")) + "\r\n").encode("utf-8")
            server.send_raw(crlf)
            self.assert_pong(server.read_json(), "crlf")
            server.send_raw(json.dumps(ping_request("tail")).encode("utf-8"))
            server.close_stdin()
            self.assert_pong(server.read_json(), "tail")
            self.assertEqual(server.wait(), 0)

    def test_invalid_utf8_then_valid_frame(self):
        with StdioServer([self.launcher], label="bad-utf8") as server:
            server.send_raw(b'{"jsonrpc":"2.0","method":"tools/call","id":"\xff"}\n')
            self.assert_error(server.read_json(), -32700)
            server.send(ping_request("after"))
            self.assert_pong(server.read_json(), "after")

    def test_size_and_depth_limits(self):
        with StdioServer([self.launcher], label="limits") as server:
            server.send_raw(b" " * (8 * 1024 * 1024 + 8) + b"\n")
            error = self.assert_error(server.read_json(), -32600)
            self.assertEqual(error["message"], "stdio message exceeds size limit")
            server.send(ping_request("size-after"))
            self.assert_pong(server.read_json(), "size-after")

            deep = json.loads("[" * 130 + "]" * 130)
            nested = rpc(
                "tools/call",
                "deep",
                {"name": "ping", "_meta": dict(META, x=deep)},
            )
            server.send(nested)
            error = self.assert_error(server.read_json(), -32600)
            self.assertEqual(error["message"], "stdio message exceeds nesting limit")
            server.send(ping_request("depth-after"))
            self.assert_pong(server.read_json(), "depth-after")

    def test_empty_eof_exits_without_output(self):
        with StdioServer([self.launcher], label="empty-eof") as server:
            server.close_stdin()
            self.assertEqual(server.wait(), 0)
            self.assertIsNone(server.read_line(timeout=2))

    def test_relocated_distribution_and_java_home(self):
        tmp = Path(tempfile.mkdtemp(prefix="mcp stdio "))
        try:
            dist = tmp / "dist with spaces"
            shutil.copytree(staged_root(self.launcher), dist)
            launcher = dist / "bin" / "mcp-stdio-example"

            fake_home = tmp / "fake java home"
            (fake_home / "bin").mkdir(parents=True)
            marker = tmp / "java-home-used"
            real_java = os.path.realpath(shutil.which("java"))
            wrapper = fake_home / "bin" / "java"
            wrapper.write_text(
                f'#!/bin/sh\ntouch "{marker}"\nexec "{real_java}" "$@"\n'
            )
            wrapper.chmod(0o755)

            cwd = tmp / "unrelated cwd"
            cwd.mkdir()
            env = dict(os.environ)
            env["JAVA_HOME"] = str(fake_home)
            with StdioServer(
                [str(launcher)], env=env, cwd=str(cwd), label="relocated"
            ) as server:
                server.send(ping_request("relocated"))
                self.assert_pong(server.read_json(), "relocated")
            self.assertTrue(marker.exists())
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def test_broken_stdout_exits_nonzero_with_static_diagnostic(self):
        server = StdioServer([self.launcher], label="broken-stdout")
        try:
            server.proc.stdout.close()
            server.send(ping_request("pipe"))
            rc = server.wait(timeout=DEADLINE)
            self.assertEqual(rc, 1)
            self.assertEqual(server.stderr_text(), "stdio transport I/O failure\n")
        finally:
            server.stop()

    def test_drain_error_fails_the_context_exit(self):
        server = StdioServer([self.launcher], label="drain-fault")
        try:
            server.send(ping_request("ok"))
            self.assert_pong(server.read_json(), "ok")
            server.drain_error = OSError("test drain failure")
            with self.assertRaisesRegex(RuntimeError, "stderr drain failed"), server:
                pass
            self.assertIsNotNone(server.proc.poll())
        finally:
            server.stop()


class LineReaderSuite(unittest.TestCase):
    """Pipe-level regression for unterminated frames at EOF."""

    def test_partial_frame_at_eof_raises(self):
        read_fd, write_fd = os.pipe()
        try:
            reader = LineReader(read_fd)
            os.write(write_fd, b"{}")
            with self.assertRaises(TimeoutError):
                reader.readline(timeout=0.01)
            os.close(write_fd)
            write_fd = None
            with self.assertRaisesRegex(ValueError, "unterminated stdout frame"):
                reader.readline(timeout=0.01)
        finally:
            os.close(read_fd)
            if write_fd is not None:
                os.close(write_fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--launcher",
        required=True,
        help="Path to the staged bin/mcp-stdio-example launcher",
    )
    args, remaining = parser.parse_known_args()
    launcher = os.path.abspath(args.launcher)
    if not os.path.isfile(launcher) or not os.access(launcher, os.X_OK):
        parser.error(
            f"launcher {launcher} not found or not executable; "
            "run 'sbt exampleStdio/stage' first"
        )
    jar = bootstrap_jar(launcher)
    if not jar.is_file():
        parser.error(
            f"bootstrap jar {jar} not found; run 'sbt exampleStdio/stage' first"
        )
    StdioExampleSuite.launcher = launcher
    StdioExampleSuite.expected_version = manifest_version(jar)
    sys.argv = [sys.argv[0], *remaining]
    unittest.main(verbosity=2)


if __name__ == "__main__":
    main()
