import sbt._
import sbt.Keys._

import scoverage._


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
      "org.apache.zookeeper" % "zookeeper" % "3.4.6" excludeAll (
        ExclusionRule(organization = "com.sun.jdmk"),
        ExclusionRule(organization = "com.sun.jmx"),
        ExclusionRule(organization = "javax.jms")
      ),
      "org.apache.curator" % "curator-test" % "2.4.2" % "test"
    ),

    publishTo <<= version { (ver: String) =>
      val nexus = "http://nexus.ataraxer.com/nexus/"
      if (ver.trim.endsWith("SNAPSHOT")) {
        Some("Snapshots" at nexus + "content/repositories/snapshots")
      } else {
        Some("Releases"  at nexus + "content/repositories/releases")
      }
    },

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { case _ => false },

    pomExtra := (
      <scm>
        <url>git@github.com:ataraxer/zooowner.git</url>
        <connection>scm:git:git@github.com:ataraxer/zooowner.git</connection>
      </scm>
      <developers>
        <developer>
          <id>ataraxer</id>
          <name>Anton Karamanov</name>
          <url>github.com/ataraxer</url>
        </developer>
      </developers>),

    parallelExecution := false
  )

  lazy val buildSettings =
    Defaults.defaultSettings ++
    ScoverageSbtPlugin.instrumentSettings ++
    Seq(
      name         := "zooowner",
      version      := "0.2.1-SNAPSHOT",
      organization := "com.ataraxer",
      homepage     := Some(url("http://github.com/ataraxer/zooowner")),
      licenses     := Seq("MIT License" -> url(
        "http://www.opensource.org/licenses/mit-license.php"
      )),
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
