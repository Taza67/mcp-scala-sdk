scalaVersion := "2.13.16"

ThisBuild / scalacOptions ++= Seq(
  "-Wunused:imports",
  "-Wunused:privates",
  "-Wunused:locals",
  "-Wunused:implicits"
)

lazy val protocol = (project in file("modules/protocol"))

lazy val codec = (project in file("modules/codec"))
  .dependsOn(protocol)

lazy val codecCirce = (project in file("modules/codec/circe"))
  .dependsOn(codec)
  .settings(
    libraryDependencies += "io.circe" %% "circe-core" % "0.14.14",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.14",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test
  )

lazy val codecZiojson = (project in file("modules/codec/ziojson"))
  .dependsOn(codec)
  .settings(
    libraryDependencies += "dev.zio" %% "zio-json" % "0.7.44",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test
  )

lazy val server = (project in file("modules/server"))
  .dependsOn(protocol)
  .settings(
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test
  )

lazy val transportStdio = (project in file("modules/transport/stdio")).dependsOn(server)

lazy val transportHttp = (project in file("modules/transport/http")).dependsOn(server)
