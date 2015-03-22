import SonatypeKeys._

val commonSettings = Seq(
  version := "0.3.0-SNAPSHOT",
  organization := "com.ataraxer",
  homepage := Some(url("http://github.com/ataraxer/zooowner")),
  licenses := Seq("MIT License" -> url(
    "http://www.opensource.org/licenses/mit-license.php")),
  scalaVersion := "2.11.6",
  crossScalaVersions := Seq("2.10.5", "2.11.6"),
  scalacOptions ++= Seq(
    "-g:vars",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint",
    "-Xfatal-warnings")) ++
  instrumentSettings

val dependencies = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    "org.apache.zookeeper" % "zookeeper" % "3.4.6" excludeAll (
      ExclusionRule(organization = "com.sun.jdmk"),
      ExclusionRule(organization = "com.sun.jmx"),
      ExclusionRule(organization = "javax.jmx"))))

val publishingSettings = sonatypeSettings ++ Seq(
  publishArtifact in Test := false,
  profileName := "ataraxer",
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
    </developers>))


lazy val zooowner = project.in(file("."))
  .aggregate(
    zooownerCore,
    zooownerActor,
    zooownerMocking)

lazy val zooownerActor = project.in(file("zooowner-actor"))
  .settings(name := "zooowner-actor")
  .dependsOn(zooownerCore, zooownerMocking)
  .settings(commonSettings: _*)
  .settings(publishingSettings: _*)
  .settings(dependencies: _*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor"   % "2.3.4",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test"))

lazy val zooownerCore = project.in(file("zooowner-core"))
  .settings(name := "zooowner-core")
  .dependsOn(zooownerMocking)
  .settings(commonSettings: _*)
  .settings(publishingSettings: _*)
  .settings(dependencies: _*)

lazy val zooownerMocking = project.in(file("zooowner-mocking"))
  .settings(name := "zooowner-mocking")
  .settings(commonSettings: _*)
  .settings(publishingSettings: _*)
  .settings(dependencies: _*)
  .settings(libraryDependencies +=
    "org.mockito" % "mockito-core" % "1.8.5")

