import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

import sbt.File
import sbt.IO
import sbt.Logger
import sbt.fileToRichFile

/**
 * Stages a relocatable `java -jar` distribution for the stdio example.
 *
 * Copies the supplied jars into `lib/`, writes a bootstrap jar whose manifest
 * Class-Path names exactly those relative lib entries, and emits a POSIX
 * launcher into `bin/`. Existing staged files are overwritten in place, never
 * deleted.
 */
object StdioDistribution {

  def stage(
      destination: File,
      jars: Seq[File],
      mainClass: String,
      version: String,
      log: Logger
  ): File = {
    require(jars.nonEmpty, "stage requires at least one jar")
    jars.foreach { jar =>
      if (!jar.isFile)
        sys.error(s"stage requires an existing jar file: $jar")
      if (jar.length() <= 0)
        sys.error(s"stage requires a nonempty jar file: $jar")
    }
    val names = jars.map(_.getName)
    val duplicates = names.diff(names.distinct).distinct
    if (duplicates.nonEmpty)
      sys.error(
        s"stage requires unique jar basenames: ${duplicates.mkString(", ")}"
      )

    val lib = destination / "lib"
    val bin = destination / "bin"
    IO.createDirectories(Seq(lib, bin))
    jars.foreach { jar =>
      Files.copy(
        jar.toPath,
        lib.toPath.resolve(jar.getName),
        StandardCopyOption.REPLACE_EXISTING
      )
    }

    val classPath = names
      .map(name => new URI(null, null, s"lib/$name", null).toASCIIString)
      .mkString(" ")
    val manifest = new Manifest()
    val attributes = manifest.getMainAttributes
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    attributes.put(Attributes.Name.MAIN_CLASS, mainClass)
    attributes.put(Attributes.Name.CLASS_PATH, classPath)
    attributes.putValue("Implementation-Version", version)
    val bootstrapPath = destination / "mcp-stdio-example.jar"
    val fileOut = new FileOutputStream(bootstrapPath)
    try new JarOutputStream(fileOut, manifest).close()
    finally fileOut.close()

    val launcher = bin / "mcp-stdio-example"
    IO.write(launcher, LauncherScript)
    if (!launcher.setExecutable(true))
      sys.error(s"stage could not mark launcher executable: $launcher")

    log.info(s"staged ${names.size} jars to ${lib.getPath}")
    destination
  }

  private val LauncherScript: String =
    """#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -P "$(dirname "$0")/.." && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi
exec "$JAVA" -jar "$APP_HOME/mcp-stdio-example.jar" "$@"
"""
}
