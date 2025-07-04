# Welcome! Thank you for contributing to Akka Cluster Management!

We follow the standard GitHub [fork & pull](https://help.github.com/articles/using-pull-requests/#fork--pull) approach to pull requests. Just fork the official repo, develop in a branch, and submit a PR!

You're always welcome to submit your PR straight away and start the discussion (without reading the rest of this wonderful doc, or the README.md). The goal of these notes is to make your experience contributing to Akka Management as smooth and pleasant as possible. We're happy to guide you through the process once you've submitted your PR.

## Build Token

To build locally, you need to fetch a token at https://account.akka.io/token that you have to place into `~/.sbt/1.0/akka-commercial.sbt` file like this:
```
ThisBuild / resolvers += "lightbend-akka".at("your token resolver here")
```

# The Akka Community

In case of questions about the contribution process or for discussion of specific issues please visit the [akka forum](https://discuss.akka.io/).

You may also check out these [other resources](https://akka.io/get-involved/).

## General Workflow

This is the process for committing code into master.

1. Make sure you have signed the Lightbend CLA, if not, [sign it online](https://www.lightbend.com/contribute/cla/akka).
2. Especially for bigger features it can be good to create or find a ticket for your work in the [issue tracker](https://github.com/akka/akka-management/issues) and discuss your proposed solution there. This is not a requirement, but can avoid disappointment later in the process.
3. Perform your work according to the [pull request requirements](#pull-request-requirements).
4. When the feature or fix is completed you should open a [Pull Request](https://help.github.com/articles/using-pull-requests) on [GitHub](https://github.com/akka/akka-management/pulls).
5. The Pull Request should be reviewed by other maintainers (as many as feasible/practical). Note that the maintainers can consist of outside contributors, both within and outside Lightbend. Outside contributors are encouraged to participate in the review process, it is not a closed process.
6. After the review you should fix the issues (review comments, CI failures) by pushing a new commit for new review, iterating until the reviewers give their thumbs up and CI tests pass.

In case of questions about the contribution process or for discussion of specific issues please visit the [akka forum](https://discuss.akka.io/).

## Pull Request Requirements

For a Pull Request to be considered at all it has to meet these requirements:

1. Pull Request branch should be given a unique descriptive name that explains its intent.
2. Code in the branch should live up to the current code standard:
   - Not violate [DRY](https://www.oreilly.com/library/view/97-things-every/9780596809515/ch30.html).
   - [Boy Scout Rule](https://www.oreilly.com/library/view/97-things-every/9780596809515/ch08.html) needs to have been applied.
3. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.
4. The code must be well documented (see the [Documentation](#documentation) section below).
5. The commit messages must properly describe the changes, see [further below](#creating-commits-and-writing-commit-messages).
6. Do not use ``@author`` tags since it does not encourage [Collective Code Ownership](http://www.extremeprogramming.org/rules/collective.html). Contributors get the credit they deserve in the release notes.

If these requirements are not met then the code should **not** be merged into master, or even reviewed - regardless of how good or important it is. No exceptions.

## Documentation

Documentation should be written in two forms:

1. API documentation in the form of scaladoc/javadoc comments on the Scala and Java user API.
2. Guide documentation in [docs](docs/) subproject using [Paradox](https://github.com/lightbend/paradox) documentation tool. This documentation should give a short introduction of how a given connector should be used.

## External Dependencies

All the external runtime dependencies for the project, including transitive dependencies, must have an open source license that is equal to, or compatible with, [Apache 2](https://www.apache.org/licenses/LICENSE-2.0).

This must be ensured by manually verifying the license for all the dependencies for the project:

1. Whenever a committer to the project changes a version of a dependency (including Scala) in the build file.
2. Whenever a committer to the project adds a new dependency.
3. Whenever a new release is cut (public or private for a customer).

Every external dependency listed in the build file must have a trailing comment with the license name of the dependency.

Which licenses are compatible with Apache 2 are defined in [this doc](https://www.apache.org/legal/resolved.html#category-a\), where you can see that the licenses that are listed under ``Category A`` automatically compatible with Apache 2, while the ones listed under ``Category B`` needs additional action:

> Each license in this category requires some degree of reciprocity. This may mean that additional action is warranted in order to minimize the chance that a user of an Apache product will create a derivative work of a differently-licensed portion of an Apache product without being aware of the applicable requirements.


## Creating Commits And Writing Commit Messages

Follow these guidelines when creating public commits and writing commit messages.

1. If your work spans multiple local commits (for example; if you do safe point commits while working in a feature branch or work in a branch for long time doing merges/rebases etc.) then please do not commit it all but rewrite the history by squashing the commits into a single big commit which you write a good commit message for (like discussed in the following sections). For more info read this article: [Git Workflow](http://sandofsky.com/blog/git-workflow.html). Every commit should be able to be used in isolation, cherry picked etc.

2. First line should be a descriptive sentence what the commit is doing, including the ticket number. It should be possible to fully understand what the commit does—but not necessarily how it does it—by just reading this single line. We follow the “imperative present tense” style for commit messages ([more info here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)).

   It is **not ok** to only list the ticket number, type "minor fix" or similar.
   If the commit is a small fix, then you are done. If not, go to 3.

3. Following the single line description should be a blank line followed by an enumerated list with the details of the commit.

4. Add keywords for your commit (depending on the degree of automation we reach, the list may change over time):
    * ``Review by @gituser`` - if you want to notify someone on the team. The others can, and are encouraged to participate.

Example:

    Add eventsByTag query #123

    * Details 1
    * Details 2
    * Details 3

## How To Enforce These Guidelines?

1. [Travis CI](https://travis-ci.org/akka/akka-management) automatically merges the code, builds it, runs the tests and sets Pull Request status accordingly of results in GitHub.
2. [Scalafmt](https://olafurpg.github.io/scalafmt) enforces some of the code style rules.
3. [sbt-header plugin](https://github.com/sbt/sbt-header) manages consistent copyright headers in every source file.
