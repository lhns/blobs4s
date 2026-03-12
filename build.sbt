ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.github.blobs4s"

lazy val root = (project in file("."))
  .settings(
    name := "blobs4s",
    javacOptions ++= Seq("--release", "21"),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.3" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
