ThisBuild / resolvers += Resolver.jcenterRepo


lazy val mimaPreviousArtifactsSet =
  mimaPreviousArtifacts := Set(
      organization.value %% name.value % previousStableVersion.value.getOrElse(
        throw new Error("Unable to determine previous version"))
    )


lazy val `cluster-bootstrap` = project
  .in(file("cluster-bootstrap"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "akka-management-cluster-bootstrap",
    libraryDependencies := Dependencies.ClusterBootstrap,
    mimaPreviousArtifactsSet
  )