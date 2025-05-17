ThisBuild / organization := "dev.rolang"
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/rolang/gar-handler"),
    "scm:git@github.com:rolang/gar-handler.git",
  )
)
ThisBuild / developers := List(
  Developer(
    id = "rolang",
    name = "Roman Langolf",
    email = "rolang@pm.me",
    url = url("https://rolang.dev"),
  )
)
ThisBuild / description := "Google Artifact Registry protocol support for coursier / sbt."
ThisBuild / licenses    := Seq(License.Apache2)
ThisBuild / homepage    := Some(url("https://github.com/rolang/gar-handler"))
ThisBuild / version ~= { v => if (v.contains('+')) s"${v.replace('+', '-')}-SNAPSHOT" else v }
ThisBuild / versionScheme          := Some("early-semver")
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeCentralHost

lazy val scala213 = "2.13.16"
lazy val scala212 = "2.12.20"

ThisBuild / scalaVersion := scala213

lazy val commonSettings = List(
  publishTo         := sonatypePublishToBundle.value,
  publishMavenStyle := true,
)

lazy val root = (project in file("."))
  .dependsOn(core, coursier, plugin)
  .aggregate(core, coursier, plugin)
  .settings(
    noPublishSettings,
    crossScalaVersions := Nil,
  )

val noPublishSettings = List(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true,
)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    moduleName         := "gar-handler",
    scalaVersion       := scala212,
    crossScalaVersions := Seq(scala212, scala213),
    Compile / sourceGenerators += Def.task {
      val file     = (Compile / sourceManaged).value / "dev" / "rolang" / "gar" / "version.scala"
      val contents = version.value
      IO.write(
        file,
        s"""|package dev.rolang.gar
            |object version { val value: String = "$contents" }""".stripMargin,
      )
      Seq(file)
    }.taskValue,
    libraryDependencies ++= Seq(
      "com.google.cloud" % "google-cloud-storage" % "2.34.0"
    ),
  )

lazy val coursier = project
  .in(file("modules/coursier"))
  .dependsOn(core)
  .aggregate(core)
  .settings(commonSettings)
  .settings(
    moduleName         := "gar-coursier",
    scalaVersion       := scala213,
    crossScalaVersions := Seq(scala213),
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "2.1.24" % Test
    ),
  )

lazy val plugin = project
  .in(file("modules/sbt"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core)
  .aggregate(core)
  .settings(commonSettings)
  .settings(
    moduleName                       := "sbt-gar-handler",
    scalaVersion                     := scala212,
    crossScalaVersions               := Seq(scala212),
    sbtPluginPublishLegacyMavenStyle := false,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
  )
