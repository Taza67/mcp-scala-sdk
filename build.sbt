scalaVersion := "2.13.16"

ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wunused:implicits"
)

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

addCommandAlias("test", "root/test")

lazy val protocol = (project in file("modules/protocol"))

lazy val codec = (project in file("modules/codec"))
  .dependsOn(protocol)
  .settings(
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val codecCirce = (project in file("modules/codec/circe"))
  .dependsOn(codec % "compile->compile;test->test")
  .settings(
    libraryDependencies += "io.circe" %% "circe-core" % "0.14.14",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.14",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val codecZiojson = (project in file("modules/codec/ziojson"))
  .dependsOn(codec % "compile->compile;test->test")
  .settings(
    libraryDependencies += "dev.zio" %% "zio-json" % "0.7.44",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test
  )

lazy val server = (project in file("modules/server"))
  .dependsOn(protocol, codec)
  .settings(
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val transportStdio = (project in file("modules/transport/stdio"))
  .dependsOn(server, codecCirce)
  .settings(
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test,
    Test / test := (Test / testFull).value
  )

lazy val exampleStdio = (project in file("examples/stdio"))
  .dependsOn(transportStdio)
  .settings(
    publish / skip := true,
    Compile / mainClass := Some("io.github.taza67.mcp.examples.stdio.Main"),
    run / fork := true,
    run / connectInput := true
  )

lazy val transportHttp = (project in file("modules/transport/http")).dependsOn(server)

lazy val root = (project in file("."))
  .aggregate(
    protocol,
    codec,
    codecCirce,
    codecZiojson,
    server,
    transportStdio,
    transportHttp,
    exampleStdio
  )
  .settings(
    publish / skip := true
  )
