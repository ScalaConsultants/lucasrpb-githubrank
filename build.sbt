
name := "githubrank"

version := "0.1"

scalaVersion := "2.13.6"

lazy val AkkaVersion = "2.6.15"
lazy val AkkaHttpVersion = "10.2.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,

  "com.typesafe.akka" %% "akka-stream-contrib" % "0.11+3-08ccb218",
  "com.typesafe.akka" %% "akka-stream-contrib" % "0.11+3-08ccb218" % Test,

  "com.github.ben-manes.caffeine" % "caffeine" % "3.0.3",
  "org.mapdb" % "mapdb" % "3.0.8",

  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,

  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion
)

dependencyOverrides += "com.typesafe.akka" %% "akka-discovery" % "2.6.15"

enablePlugins(AkkaGrpcPlugin)
