import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.0"

  val AkkaVersion = "2.6.8"
  val AkkaHttpVersion = "10.2.0"
  lazy val akka = "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % AkkaVersion
  lazy val akkaStreamTyped = "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion

  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val CirceVersion = "0.12.3"

  lazy val circe = "io.circe" %% "circe-core" % CirceVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % CirceVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % CirceVersion

  lazy val all = 
    akka :: akkaStream :: akkaStreamTyped :: akkaHttp :: logback :: 
    circe :: circeGeneric :: circeParser :: (scalaTest % Test) :: Nil
}
