## Releasing

1. Create a [new release](https://github.com/akka/akka-cluster-management/releases/new) with the next tag version (e.g. `v0.3`), title and release decsription including notable changes mentioning external contributors.
2. Travis CI will start a [CI build](https://travis-ci.org/akka/akka-cluster-management/builds) for the new tag and publish artifacts to Bintray.
3. Login to [Bintray](https://bintray.com/akka/maven/akka-cluster-management) and sync artifacts to Maven Central.
4. Change documentation links to point to the latest version in the README.md and the Github project page.
