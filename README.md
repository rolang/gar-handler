[![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.rolang/gar-coursier_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/rolang/gar-coursier_2.13/)

# Google Artifact Registry support for coursier

Adds handler for `artifactregistry://` protocol to [coursier](https://get-coursier.io/)

#### Run test

Ensure default google credentials are set up. Run:

```scala
// GetVersions test args: 1. maven root, 2. organization, 3. module name
sbt 'Test/runMain GetVersions asia-maven.pkg.dev/my-project/maven com.example my-package_2.13'
```
