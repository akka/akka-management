Release Akka Management $VERSION$

<!--
# Release Train Issue Template for Akka Management

(Liberally copied and adopted from Scala itself https://github.com/scala/scala-dev/blob/b11cd2e4a4431de7867db6b39362bea8fa6650e7/notes/releases/template.md)

For every release, make a copy of this file named after the release, and expand the variables.
Ideally replacing variables could become a script you can run on your local machine.

Variables to be expanded in this template:
- $VERSION$=???
-->

### before the release

- [ ] Check that open PRs and issues assigned to the milestone are reasonable
- [ ] Create a new milestone for the [next version](https://github.com/akka/akka-management/milestones)
- [ ] Check [closed issues without a milestone](https://github.com/akka/akka-management/issues?utf8=%E2%9C%93&q=is%3Aissue%20is%3Aclosed%20no%3Amilestone) and either assign them the 'upcoming' release milestone or `invalid/not release-bound`

### Preparing release notes in the documentation / announcement

- For non-patch releases: 
    - [ ] Create a news item draft PR on [akka.github.com](https://github.com/akka/akka.github.com), based on the [draft release](https://github.com/akka/akka-management/releases) and [`sbt-authors`](https://github.com/2m/authors) (eg. `./scripts/authors.scala v1.0.9 HEAD`).
- [ ] Move all [unclosed issues](https://github.com/akka/akka-management/issues?q=is%3Aopen+is%3Aissue+milestone%3A$VERSION$) for this milestone to the next milestone
- [ ] Close the [$VERSION$ milestone](https://github.com/akka/akka-management/milestones?direction=asc&sort=due_date)

### Cutting the release

- [ ] Wait until the build for the `main` branch finished on GH Actions after the last merge
- [ ] `git tag -a v$VERSION$ -m "Release version $VERSION$"; git push --tags`
- [ ] Check that GitHub Actions release build has executed successfully (it should publish artifacts to Sonatype and documentation to Gustav)

### Check availability

- [ ] Check [API](https://doc.akka.io/api/akka-management/$VERSION$/) documentation
- [ ] Check [reference](https://doc.akka.io/docs/akka-management/$VERSION$/) documentation
- [ ] Check the release on [Maven central](https://repo1.maven.org/maven2/com/lightbend/akka/management/akka-management_2.12/$VERSION$/)

### When everything is on maven central

  - `ssh akkarepo@gustav.akka.io`
    - [ ] update the `current` links on `repo.akka.io` to point to the latest version
         ```
         ln -nsf $VERSION$ www/docs/akka-management/current
         ln -nsf $VERSION$ www/api/akka-management/current
         ```
    - [ ] check changes and commit the new version to the local git repository
         ```
         cd ~/www
         git add docs/akka-management/ api/akka-management/
         git commit -m "Akka Management $VERSION$"
         ```

### Announcements

- [ ] Update the [draft release](https://github.com/akka/akka-management/releases), either with the content from the news item, or by revising and adding the [`sbt-authors`](https://github.com/2m/authors) (eg. `./scripts/authors.scala v1.0.9 HEAD`) to the generated text. Attach to the tag created earlier, and publish.
- [ ] For non-patch releases: Merge draft news item for [akka.io](https://github.com/akka/akka.github.com)
- For non-patch releases or for a particularly interesting patch release: 
    - [ ] Send a release notification to [Lightbend discuss](https://discuss.akka.io)
    - [ ] Tweet using the akkateam account (or ask someone to) about the new release
    - [ ] Announce on [Gitter akka/akka](https://gitter.im/akka/akka)
    - [ ] Announce internally

### Afterwards

- [ ] Update version for [Lightbend Supported Modules](https://developer.lightbend.com/docs/lightbend-platform/introduction/getting-help/build-dependencies.html#_akka_management) in [private project](https://github.com/lightbend/lightbend-technology-intro-doc/blob/master/docs/modules/getting-help/examples/build.sbt#L156)
- Close this issue
