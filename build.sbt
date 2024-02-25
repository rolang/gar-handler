ThisBuild / organization := "dev.rolang"
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/rolang/gar-coursier"),
    "scm:git@github.com:rolang/gar-coursier.git",
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
ThisBuild / description := "Google Artifact Registry protocol support for coursier."
ThisBuild / licenses    := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage    := Some(url("https://github.com/rolang/gar-coursier"))

lazy val scala213 = "2.13.13"
lazy val scala212 = "2.12.18"

lazy val commonSettings = List(
  version := "0.1.2",
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    sys.env.getOrElse("SONATYPE_USERNAME", ""),
    sys.env.getOrElse("SONATYPE_PASSWORD", ""),
  ),
  usePgpKeyHex("537C76F3EFF1B9BE6FD238B442BD95234C9636F3"),
  sonatypeCredentialHost := "oss.sonatype.org",
  sonatypeRepository     := s"https://${sonatypeCredentialHost.value}/service/local",
  publishTo              := sonatypePublishToBundle.value,
  publishMavenStyle      := true,
)

lazy val root = (project in file("."))
  .dependsOn(core, coursier, plugin)
  .aggregate(core, coursier, plugin)
  .settings(noPublishSettings)

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
    crossScalaVersions := Nil,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "2.1.9" % Test
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
    crossScalaVersions               := Nil,
    sbtPluginPublishLegacyMavenStyle := false,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
  )
