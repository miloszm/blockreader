name := """blockreader"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

val catsVersion = "2.0.0"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0" % Test,
  "org.typelevel" %% "cats-core" % catsVersion,
  "com.typesafe.akka" %% "akka-actor" % "2.6.9",
  "com.typesafe.akka" %% "akka-stream" % "2.6.9",
  "com.typesafe.akka" %% "akka-slf4j" % "2.6.9",
  "com.typesafe.akka" %% "akka-http" % "10.1.12",
  "net.codingwell" %% "scala-guice" % "4.2.6",
  "org.webjars" %% "webjars-play" % "2.7.3",
  "org.bitcoin-s" %% "bitcoin-s-core" % "0.4.0",
  "org.bitcoin-s" %% "bitcoin-s-bitcoind-rpc" % "0.4.0",
  "org.bitcoin-s" %% "bitcoin-s-testkit" % "0.4.0" % Test
)

