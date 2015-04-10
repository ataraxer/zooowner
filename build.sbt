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
    "org.parboiled" %% "parboiled" % "2.1.0",
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
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

val ghPagesSettings = {
  site.settings ++
  ghpages.settings ++
  site.includeScaladoc("") ++ Seq {
    git.remoteRepo := "git@github.com:ataraxer/zooowner.git"
  }
}

val projectSettings = {
  commonSettings ++
  publishingSettings ++
  dependencies
}


lazy val zooowner = project.in(file("."))
  .aggregate(
    zooownerCore,
    zooownerActor,
    zooownerMocking)

lazy val zooownerCore = project.in(file("zooowner-core"))
  .settings(name := "zooowner-core")
  .dependsOn(zooownerMocking)
  .settings(projectSettings: _*)
  .settings(ghPagesSettings: _*)

lazy val zooownerActor = project.in(file("zooowner-actor"))
  .settings(name := "zooowner-actor")
  .dependsOn(zooownerCore, zooownerMocking)
  .settings(projectSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor"   % "2.3.4",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test"))

lazy val zooownerMocking = project.in(file("zooowner-mocking"))
  .settings(name := "zooowner-mocking")
  .settings(projectSettings: _*)
  .settings(libraryDependencies +=
    "org.mockito" % "mockito-core" % "1.8.5")

