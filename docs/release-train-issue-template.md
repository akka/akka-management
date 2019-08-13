# Release Train Issue Template for Akka Management

(Liberally copied and adopted from Scala itself https://github.com/scala/scala-dev/blob/b11cd2e4a4431de7867db6b39362bea8fa6650e7/notes/releases/template.md)

For every release, make a copy of this file named after the release, and expand the variables.
Ideally replacing variables could become a script you can run on your local machine.

Variables to be expanded in this template:
- $AKKA_MANAGEMENT_VERSION$=???

### before the release

- [ ] Check that any new `deprecated` annotations use the correct version name
- [ ] Check that open PRs and issues assigned to the milestone are reasonable
- [ ] Create a new milestone for the [next version](https://github.com/akka/akka-management/milestones)
- [ ] Check [closed issues without a milestone](https://github.com/akka/akka-management/issues?utf8=%E2%9C%93&q=is%3Aissue%20is%3Aclosed%20no%3Amilestone) and either assign them the 'upcoming' release milestone or `invalid/not release-bound`
- [ ] Make sure all important / big PRs have been merged by now

### Preparing release notes in the documentation / announcement

- [ ] For non-patch releases: rename the 'akka-management-x.x-stable' reporting projects in [WhiteSource](https://saas.whitesourcesoftware.com/Wss/WSS.html) accordingly (unfortunately this requires permissions that cannot be shared outside of Lightbend)
- [ ] For non-patch releases: Create a news item draft PR on [akka.github.com](https://github.com/akka/akka.github.com), using the milestone
- [ ] Move all [unclosed issues](https://github.com/akka/akka-management/issues?q=is%3Aopen+is%3Aissue+milestone%3A$AKKA_MANAGEMENT_VERSION$) for this milestone to the next milestone

### Cutting the release

- [ ] Wait until [master build finished](https://travis-ci.org/akka/akka-management/builds/) after the last merge
- [ ] Create a [new release](https://github.com/akka/akka-management/releases/new) with notes from the [milestone](https://github.com/akka/akka-management/milestones/$AKKA_MANAGEMENT_VERSION$) and [`sbt-authors`](https://github.com/2m/authors) (eg. `sbt "authors v0.22 HEAD"`)
- [ ] Check that Travis CI release build has executed successfully (Travis will start a [CI build](https://travis-ci.org/akka/akka-management/builds) for the new tag and publish artifacts to Bintray and documentation to Gustav)
- [ ] Go to [Bintray](https://bintray.com/akka/maven/akka-management/$AKKA_MANAGEMENT_VERSION$), go to the Maven Central tab, check the *Close and release repository when done* checkbox and sync with Sonatype (using your Sonatype TOKEN key and password)

### Check availability

- [ ] Check [API](https://doc.akka.io/api/akka-management/$AKKA_MANAGEMENT_VERSION$/) documentation
- [ ] Check [reference](https://doc.akka.io/docs/akka-management/$AKKA_MANAGEMENT_VERSION$/) documentation
- [ ] Check the release on [Maven central](http://central.maven.org/maven2/com/lightbend/akka/management/akka-management_2.12/$AKKA_MANAGEMENT_VERSION$/)

### When everything is on maven central
  - [ ] `ssh akkarepo@gustav.akka.io`
    - [ ] update the `current` links on `repo.akka.io` to point to the latest version with (**replace the minor appropriately**)
         ```
         ln -nsf $AKKA_MANAGEMENT_VERSION$ www/docs/akka-management/1.0
         ln -nsf $AKKA_MANAGEMENT_VERSION$ www/api/akka-management/1.0
         ln -nsf $AKKA_MANAGEMENT_VERSION$ www/docs/akka-management/current
         ln -nsf $AKKA_MANAGEMENT_VERSION$ www/api/akka-management/current
         ```
    - [ ] check changes and commit the new version to the local git repository
         ```
         cd ~/www
         git add docs/akka-management/ api/akka-management/
         git commit -m "Akka Management $AKKA_MANAGEMENT_VERSION$"
         ```

### Announcements

- [ ] For non-patch releases: Merge draft news item for [akka.io](https://github.com/akka/akka.github.com)
- [ ] Send a release notification to [Lightbend discuss](https://discuss.akka.io)
- [ ] Tweet using the akkateam account (or ask someone to) about the new release
- [ ] Announce on [Gitter akka/akka](https://gitter.im/akka/akka)
- [ ] Announce internally

### Afterwards

- [ ] Update version for [Lightbend Supported Modules](https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-help/build-dependencies.html#_akka_management) in [private project](https://github.com/lightbend/lightbend-platform-docs/blob/master/docs/modules/getting-help/examples/build.sbt#L153)
- [ ] Close the [$AKKA_MANAGEMENT_VERSION$ milestone](https://github.com/akka/akka-management/milestones?direction=asc&sort=due_date)
- Close this issue
