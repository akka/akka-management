## Releasing

1. Create a [new release](https://github.com/akka/akka-management/releases/new) with the next tag version (e.g. `v0.7`), title and release description including notable changes mentioning external contributors.
1. Travis CI will start a [CI build](https://travis-ci.org/akka/akka-management/builds) for the new tag and publish artifacts to Bintray.
1. Login to [Bintray](https://bintray.com/akka/maven/akka-management) and sync artifacts to Maven Central.
1. https://developer.lightbend.com/docs/akka-management/current/ should automatically sync after a while
1. Post an announcement to https://github.com/akka/akka.github.com/
1. Post an announcement to https://discuss.akka.io
1. Post an announcement to the Lightbend Engineering Updates mailinglist
1. Update version in https://github.com/lightbend/reactive-platform-docs/blob/master/build.sbt
1. Close the current milestone in GitHub and create the next one

### Releasing only updated docs

It is possible to release a revised documentation to the already existing release.

1. Create a new branch from a release tag. If a revised documentation is for the `v0.7` release, then the name of the new branch should be `docs/v0.7`.
2. Make all of the required changes to the documentation.
3. Add and commit `version.sbt` file that sets the version to the one, that is being revised. For example `version in ThisBuild := "0.7"`.
4. Push the branch. Tech Hub will see the new branch and will build and publish revised documentation.
