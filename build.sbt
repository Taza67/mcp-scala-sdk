scalaVersion := "2.13.16"

lazy val protocol = (project in file("modules/protocol"))

lazy val server = (project in file("modules/server")).dependsOn(protocol)

lazy val transportStdio = (project in file("modules/transport/stdio")).dependsOn(server)

lazy val transportHttp = (project in file("modules/transport/http")).dependsOn(server)
