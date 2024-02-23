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

lazy val root = project
  .in(file("."))
  .settings(
    name         := "gar-coursier",
    version      := "0.1.0",
    isSnapshot   := false,
    scalaVersion := "2.13.12",
    credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sys.env.getOrElse("SONATYPE_USERNAME", ""),
      sys.env.getOrElse("SONATYPE_PASSWORD", ""),
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier"             % "2.1.9" % Test,
      "com.google.cloud" % "google-cloud-storage" % "2.34.0",
    ),
  )
