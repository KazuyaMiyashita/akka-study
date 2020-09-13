import Dependencies._

ThisBuild / scalaVersion     := "2.13.2"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val commonSettings = Seq(
  scalacOptions ++= "-deprecation" :: "-feature" :: "-Xlint" :: Nil,
  scalacOptions in (Compile, console) ~= {_.filterNot(_ == "-Xlint")},
  scalafmtOnCompile := true
)

lazy val root = (project in file("."))
  .settings(
    name := "akka-study",
    commonSettings
  )
  .aggregate(
    akka01,
    akka02,
    akka03,
    akka04,
    akka05,
    akka06,
    akka07,
    akka08,
    akka09,
    akka10,
    akka11,
    akka12,
    akka13,
    akka14
  )

lazy val akka01 = (project in file("akka01"))
  .settings(
    name := "akka-study-01",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka02 = (project in file("akka02"))
  .settings(
    name := "akka-study-02",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka03 = (project in file("akka03"))
  .settings(
    name := "akka-study-03",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka04 = (project in file("akka04"))
  .settings(
    name := "akka-study-04",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka05 = (project in file("akka05"))
  .settings(
    name := "akka-study-05",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka06 = (project in file("akka06"))
  .settings(
    name := "akka-study-06",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka07 = (project in file("akka07"))
  .settings(
    name := "akka-study-07",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka08 = (project in file("akka08"))
  .settings(
    name := "akka-study-08",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka09 = (project in file("akka09"))
  .settings(
    name := "akka-study-09",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka10 = (project in file("akka10"))
  .settings(
    name := "akka-study-10",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka11 = (project in file("akka11"))
  .settings(
    name := "akka-study-11",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka12 = (project in file("akka12"))
  .settings(
    name := "akka-study-12",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka13 = (project in file("akka13"))
  .settings(
    name := "akka-study-13",
    commonSettings,
    libraryDependencies ++= all
  )

lazy val akka14 = (project in file("akka14"))
  .settings(
    name := "akka-study-14",
    commonSettings,
    libraryDependencies ++= all
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
