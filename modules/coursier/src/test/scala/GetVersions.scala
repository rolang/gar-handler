import coursier._

object GetVersions {
  // args: 1. maven root, 2. organization, 3. module name
  // example: asia-maven.pkg.dev/my-project/maven com.example my-package_2.13
  def main(args: Array[String]): Unit = {
    val mavenRoot = "artifactregistry://" + args.head
    val module = Module(organization = Organization(args(1)), name = ModuleName(args(2)))

    val versions: Versions.Result = Versions
      .apply()
      .addRepositories(
        new MavenRepository(root = mavenRoot)
      )
      .withModule(module)
      .runResult()

    assert(
      versions.results.exists {
        case (r: MavenRepository, Right(versions)) => r.root == mavenRoot && versions.available.nonEmpty
        case _                                     => false
      },
      "versions not found"
    )

    println(s"Test versions: ${versions.results.mkString("\n")}")
  }
}
