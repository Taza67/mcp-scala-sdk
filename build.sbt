ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "io.github.taza67"
ThisBuild / version := "0.1.0-alpha.1"
ThisBuild / homepage := Some(uri("https://github.com/Taza67/mcp-scala-sdk"))
ThisBuild / licenses := List(
  "Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0")
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://github.com/Taza67/mcp-scala-sdk"),
    "scm:git:https://github.com/Taza67/mcp-scala-sdk.git",
    Some("scm:git:git@github.com:Taza67/mcp-scala-sdk.git")
  )
)

ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wunused:implicits",
  "-Werror",
  "-release:17"
)

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

addCommandAlias("test", "root/test")

lazy val stage =
  taskKey[File]("Stage the stdio example as a relocatable java -jar distribution")

lazy val protocol = (project in file("modules/protocol"))
  .settings(
    name := "mcp-protocol"
  )

lazy val codec = (project in file("modules/codec"))
  .dependsOn(protocol)
  .settings(
    name := "mcp-codec",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val codecCirce = (project in file("modules/codec/circe"))
  .dependsOn(codec % "compile->compile;test->test")
  .settings(
    name := "mcp-codec-circe",
    libraryDependencies += "io.circe" %% "circe-core" % "0.14.14",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.14",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val codecZiojson = (project in file("modules/codec/ziojson"))
  .dependsOn(codec % "compile->compile;test->test")
  .settings(
    name := "mcp-codec-zio-json",
    libraryDependencies += "dev.zio" %% "zio-json" % "0.7.44",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val client = (project in file("modules/client"))
  .dependsOn(protocol, codec)
  .settings(
    name := "mcp-client",
    publish / skip := true,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val server = (project in file("modules/server"))
  .dependsOn(protocol, codec)
  .settings(
    name := "mcp-server",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val transportStdio = (project in file("modules/transport/stdio"))
  .dependsOn(server, codecCirce)
  .settings(
    name := "mcp-transport-stdio",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val exampleStdio = (project in file("examples/stdio"))
  .dependsOn(transportStdio)
  .settings(
    name := "mcp-stdio-example",
    publish / skip := true,
    Compile / mainClass := Some("io.github.taza67.mcp.examples.stdio.Main"),
    run / fork := true,
    run / connectInput := true,
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "io" / "github" / "taza67" /
        "mcp" / "examples" / "stdio" / "ExampleBuildInfo.scala"
      val literal = version.value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
      IO.write(
        file,
        s"""package io.github.taza67.mcp.examples.stdio
           |
           |/** Example build metadata generated from the build version. */
           |object ExampleBuildInfo {
           |  val version: String = "$literal"
           |}
           |""".stripMargin
      )
      Seq(file)
    }.taskValue,
    stage := Def.uncached {
      def file(ref: xsbti.HashedVirtualFileRef): File =
        fileConverter.value.toPath(ref).toFile
      val projectRoot = (LocalRootProject / baseDirectory).value
      val license = projectRoot / "LICENSE"
      require(license.isFile, "stage requires repository LICENSE")
      val staged = StdioDistribution.stage(
        destination = projectRoot / "target" / "stdio-example",
        jars = Seq(
          file((protocol / Compile / packageBin).value),
          file((codec / Compile / packageBin).value),
          file((codecCirce / Compile / packageBin).value),
          file((server / Compile / packageBin).value),
          file((transportStdio / Compile / packageBin).value),
          file((Compile / packageBin).value)
        ) ++ (Runtime / externalDependencyClasspath).value
          .map(entry => file(entry.data))
          .filter(entry => entry.getName.endsWith(".jar")),
        mainClass = (Compile / mainClass).value
          .getOrElse(sys.error("exampleStdio main class is not set")),
        version = version.value,
        log = streams.value.log
      )
      IO.copyFile(license, staged / "LICENSE")
      staged
    }
  )

lazy val transportHttp = (project in file("modules/transport/http"))
  .dependsOn(server, codecCirce % "test->compile")
  .settings(
    name := "mcp-transport-http",
    publish / skip := true,
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val root = (project in file("."))
  .aggregate(
    protocol,
    codec,
    codecCirce,
    codecZiojson,
    client,
    server,
    transportStdio,
    transportHttp,
    exampleStdio
  )
  .settings(
    publish / skip := true
  )
