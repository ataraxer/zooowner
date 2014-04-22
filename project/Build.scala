import sbt._
import sbt.Keys._


object AkkaPattersBuild extends Build {

  val akkaVersion = "2.3.1"
  val scalatestVersion = "2.1.0"

  lazy val commonSettings = Seq(
    scalacOptions ++= Seq(
      "-g:vars",
      "-deprecation",
      "-unchecked",
      "-feature",
      "-Xlint",
      "-Xfatal-warnings"
    ),

    resolvers ++= Seq(
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    ),

    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      // ScalaTest
      "org.scalatest"     %% "scalatest"    % scalatestVersion  % "test",
      // ZooKeeper
      "org.apache.zookeeper" % "zookeeper" % "3.3.6" excludeAll (
        ExclusionRule(organization = "com.sun.jdmk"),
        ExclusionRule(organization = "com.sun.jmx"),
        ExclusionRule(organization = "javax.jms")
      )
    ),

    parallelExecution := false
  )

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    name         := "zooowner",
    version      := "0.1.0",
    scalaVersion := "2.10.3"
  )

  lazy val zooowner = Project(
    id = "zooowner",
    base = file("."),
    settings = buildSettings
  ).settings(
    commonSettings: _*
  )

}


// vim: set ts=2 sw=2 et:
