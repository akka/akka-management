Release Akka Management $VERSION$

<!--
# Release Train Issue Template for Akka Management

(Liberally copied and adopted from Scala itself https://github.com/scala/scala-dev/blob/b11cd2e4a4431de7867db6b39362bea8fa6650e7/notes/releases/template.md)

For every release, use the `scripts/create-release-issue.sh` to make a copy of this file named after the release, and expand the variables.

Variables to be expanded in this template:
- $VERSION$=???
-->

### Cutting the release

- [ ] Check that open PRs and issues assigned to the milestone are reasonable
- [ ] Update the Change date and version in the LICENSE file.
- [ ] Create a new milestone for the [next version](https://github.com/akka/akka-management/milestones)
- [ ] Close the [$VERSION$ milestone](https://github.com/akka/akka-management/milestones?direction=asc&sort=due_date)
- [ ] Make sure all important PRs have been merged
- [ ] Wait until [main build finished](https://github.com/akka/akka-management/actions) after merging the latest PR
- [ ] Update the [draft release](https://github.com/akka/akka-management/releases) with the next tag version `v$VERSION$`, title and release description. Use the `Publish release` button, which will create the tag.
- [ ] Check that GitHub Actions release build has executed successfully (GitHub Actions will start a [CI build](https://github.com/akka/akka-management/actions) for the new tag and publish artifacts to https://repo.akka.io/maven)

### Check availability

- [ ] Check [API](https://doc.akka.io/api/akka-management/$VERSION$/) documentation
- [ ] Check [reference](https://doc.akka.io/docs/akka-management/$VERSION$/) documentation. Check that the reference docs were deployed and show a version warning (see section below on how to fix the version warning).
- [ ] Check the release on https://repo.akka.io/maven/com/lightbend/akka/management/akka-management_2.13/$VERSION$/akka-management_2.13-$VERSION$.pom

### When everything is on https://repo.akka.io/maven
  - [ ] Log into `gustav.akka.io` as `akkarepo` 
    - [ ] If this updates the `current` version, run `./update-akka-management-current-version.sh $VERSION$`
    - [ ] otherwise check changes and commit the new version to the local git repository
         ```
         cd ~/www
         git status
         git add docs/akka-management/current docs/akka-management/$VERSION$
         git add api/akka-management/current api/akka-management/$VERSION$
         git commit -m "Akka Management $VERSION$"
         ```

### Announcements

For important patch releases, and only if critical issues have been fixed:

- [ ] Send a release notification to [Lightbend discuss](https://discuss.akka.io)
- [ ] Tweet using the [@akkateam](https://twitter.com/akkateam/) account (or ask someone to) about the new release
- [ ] Announce on [Gitter akka/akka](https://gitter.im/akka/akka)
- [ ] Announce internally (with links to Tweet, discuss)

For minor or major releases:

- [ ] Include noteworthy features and improvements in Akka umbrella release announcement at akka.io. Coordinate with PM and marketing.

### Afterwards

- [ ] Update [akka-dependencies bom](https://github.com/lightbend/akka-dependencies) and version for [Akka module versions](https://doc.akka.io/docs/akka-dependencies/current/) in [akka-dependencies repo](https://github.com/akka/akka-dependencies)
- [ ] Update [Akka Guide samples](https://github.com/akka/akka-platform-guide)
- Close this issue

