[![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.rolang/gar-coursier_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/rolang/gar-coursier_2.13/)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/dev.rolang/gar-coursier_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/dev.rolang/gar-coursier_2.13/)

# Google Artifact Registry support for sbt and/or coursier

Adds handler for `artifactregistry://` protocol to [sbt](https://www.scala-sbt.org/) and/or [coursier](https://get-coursier.io/).  
Inspired by [abdolence/sbt-gcs-resolver](https://github.com/abdolence/sbt-gcs-resolver) and [987Nabil/gar-coursier](https://github.com/987Nabil/gar-coursier).

Supports `artifactregistry` only.  
Splitted into modules that can also be used without sbt and/or only with coursier by adding a custom protocol handler.

## Usage

To use the sbt plugin add to `project/plugins.sbt`:

```scala
lazy val garHandlerVersion = "0.1.2-SNAPSHOT"

addSbtPlugin("dev.rolang" % "sbt-gar-handler" % garHandlerVersion)

// this is optional, add it if you have artifactregistry:// resolvers for build dependencies
csrConfiguration := csrConfiguration.value.withProtocolHandlerDependencies(
  Seq("dev.rolang" % "gar-coursier_2.13" % garHandlerVersion)
)
```

The plugin is automatically enabled for all projects and will install a global `artifactregistry://` handler
as well as adding the protocol handler to coursier.

To use with coursier only use the `dev.rolang:gar-coursier_2.13` package.  
Example usage via the coursier JVM CLI (ensure default google credentials are set up):

```bash
cs launch coursier:2.1.9 dev.rolang:gar-coursier_2.13:0.1.2 -- \
    resolve \
    -r '!artifactregistry://LOCATION-maven.pkg.dev/PROJECT/REPOSITORY' -r central \
    org:name:ver
```

## Development

#### Run test

Ensure default google credentials are set up. Run:

```scala
// GetVersions test args: 1. maven root, 2. organization, 3. module name
sbt '++2.13; coursier/Test/runMain GetVersions asia-maven.pkg.dev/my-project/maven com.example my-package_2.13'
```

#### Publish local:

```scala
sbt '++2.13; coursier/publishLocal; ++2.12; plugin/publishLocal'
```
