![Header](./readme/vanillabp-headline.png)

# A runtime assembled from published workflow modules

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

The scenario is the one larger organisations actually run: teams publish their workflow
modules as JARs, and somebody assembles a runtime from them without having their sources.
This blueprint is that somebody's project. Its application is a POM, a configuration file and
two tests - no Java code wiring anything, because it has nothing to wire with.

A delta on top of [`module-multi`](https://github.com/vanillabp-blueprints/module-multi-quarkus),
whose modules these are. What changes is where they come from: their own group, their own
version, their own package, and no relationship to this project other than a dependency.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

![The loan repayment process](docs/loan_repayment.png)

Two use cases, two JARs, published by their teams as `com.acme.lending:loan-approval:1.5.0`
and `com.acme.lending:loan-repayment:2.2.0`. This project has neither their sources nor a say
in their versions; it collects them, configures the environment they run in, and ships the
result.

### Discovery: what it takes to pick up a foreign module

Nothing, and that is the point. The modules live in `com.acme.*`, packages this project never
heard of, and all it takes is the dependency: each JAR carries an index of its classes, so the
build of the application finds the beans without being told where to look. The smoke test is
what proves it: it holds every workflow module declaring itself on the classpath against the
ones VanillaBP actually wired.

**Where that comes from** is `module-multi`: a module builds its index with the
`jandex-maven-plugin`, and
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#provide-index-for-workflow-modules)
says when it is needed and when the marker file alone is enough.

**And what to do with a module which has no index**, because that one shows up in practice:
the application indexes it from outside with `quarkus.index-dependency.<name>.group-id` and
`.artifact-id`. It works, and it makes this project responsible for a decision belonging to
the module's build. Ask the team for the plugin instead; this is the fallback, not the way.

### Versioning: a module update is a dependency update

Every module carries its own version, and the application pins them in one place:

```xml
<dependencyManagement>
  <dependency>
    <groupId>com.acme.lending</groupId>
    <artifactId>loan-approval</artifactId>
    <version>1.5.0</version>
  </dependency>
  ...
```

Taking a new version of a module is a line changed there and nothing else. Its BPMN models
come along in the JAR, and what happens to workflows already running under the old models is
not this project's decision either: VanillaBP deploys what it finds at startup, and how a BPMS
treats instances of an older version is
[`bpmn-versioning`](https://github.com/vanillabp-blueprints/bpmn-versioning-quarkus).

### Configuration: three places, and which one wins

The same value can be set by the module, by this application and by the environment, and the
order is the one an assembling project needs:

|                 Set in                 |                 Wins over                 |
|----------------------------------------|-------------------------------------------|
| environment, system property           | everything                                |
| `application.yaml` of this application | the file a module brings along            |
| the module's own file                  | nothing, it carries the module's defaults |

`application.yaml` of this blueprint sets both values a module ships, and `ConfigurationLevelsIT`
reads what arrived: `rating-scale` becomes the 42 set here rather than the module's 100, and
`rating-provider` takes the value the build passes in from outside.

What that means for assembling modules you do not own: **their configuration is yours to
decide**. A module's file says what it needs to run at all, and the project which collects it
says what its environment needs. The one exception is a module's profile-specific file, which
beats its plain one, because that is still the module talking about itself.

Early snapshots of VanillaBP 2 had it the other way round, so a project written against one of
them may rely on a module winning; it does not any more. The framework documents the order and that
correction in
[Workflow modules in Quarkus](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration)
and in
[Migrating from Version 1](https://github.com/vanillabp/adapter-platform-integration/wiki/Migrating-from-version-1#a-workflow-modules-own-files-are-still-defaults).

### Shipping it

Three artifacts come out of this project, and none of them mentions a module:

```bash
mvn install                                        # the runnable application in application/target/quarkus-app
mvn install -Dquarkus.container-image.build=true   # the image: online-banking:1.0.0-SNAPSHOT
```

The image is assembled by Jib, which needs no Docker daemon, and it carries the version of the
*application*: which module versions went in is the POM's business. Building it is not part of
`verify` or of CI - it takes minutes and downloads a base image, and a test which starts a
container tests Docker rather than this blueprint.

**The native build is where this blueprint currently stops**, and saying so is more useful than
leaving it out. The command is

```bash
mvn install -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

and today it ends in the image builder with `Discovered unresolved method ...
QuarkusMongoDeployment.isReplicaSet()`, caused by `NoClassDefFoundError: org/bson/conversions/Bson`.
VanillaBP reaches a MongoDB class from a code path this application never takes, which the JVM
never notices and a native image refuses at build time. It is a platform issue, it is reported,
and nothing in this project works around it: an application which needs a native image today
either brings the MongoDB driver along or waits for the fix.

The engine stays a Maven profile through all of it: `-Pcamunda8` changes what is packaged, what
the tests run against and what the image contains, without one line of Java moving.

### What comes from `module-multi` unchanged

Everything about having two workflow modules is that blueprint's subject and is not repeated
here: each module carries its index, keeps its resources below a directory of its own, and the
application sets `name-clash-avoidance: use-prefix` so the modules cannot collide inside the
engine.

## Delta to the base blueprint

Compared to [`module-multi`](https://github.com/vanillabp-blueprints/module-multi-quarkus),
whose modules this project collects:

|                                     File                                     |                                    What is different                                     |
|------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `loan-approval/pom.xml`, `loan-repayment/pom.xml`, `banking-commons/pom.xml` | no parent, own group, own version, own build including their index: JARs of another team |
| `com/acme/...`                                                               | the modules live in packages this application never heard of                             |
| `application/pom.xml`                                                        | pins the module versions in `dependencyManagement` and adds the image extension          |
| `application/src/main/resources/application.yaml`                            | tries to override two module values, and the test reads which attempt arrives            |
| `application/src/test/.../ConfigurationLevelsIT.java`                        | new: which of the three configuration levels wins                                        |
| `application/src/test/.../ApplicationSmokeTest.java`                         | unchanged, but here it is the test of the discovery rather than a formality              |
| `ModuleConfigurationPerProfileIT`, `BeanNamesPerModuleIT`                    | gone: profiles and bean names are `module-multi`'s subject                               |

Everything inside the modules is `module-multi`, file for file. Only their coordinates, their
packages and their build are those of somebody else's release.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run; its address lives in
`application/src/main/resources/application-camunda8.yaml`, with a copy for each module's own
test.

Start the application:

```bash
mvn -pl application quarkus:dev
```

Two URLs start something, one per workflow module:

```
http://localhost:8080/api/loan-approval/start?customerId=C-1001&amount=5000
http://localhost:8080/api/loan-repayment/start?customerId=C-1002&amount=6000
```

Each answers with the ID of the case it started and logs the URL showing the result:

```
Loan approval '0f7c…' started: Ada Lovelace asks for 5,000 EUR
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

Both URLs are a module's own, published by that module, and this project neither knows nor
declares them.

Wherever the engine shows its deployments, it answers the shortest version of "what is
actually in this runtime": two processes, each prefixed with the module which brought it.

To run the image instead of the application:

```bash
mvn install -Dquarkus.container-image.build=true
docker run --rm -p 8080:8080 -e LOAN_APPROVAL_RATING_PROVIDER=schufa online-banking:1.0.0-SNAPSHOT
```

The environment variable is the third configuration level from above, and the log line naming
the rating provider is where it becomes visible.

## How it works

|                          File                           |                                             Role                                              |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `application/pom.xml`                                   | the whole project: two module dependencies, their pinned versions, the adapter and the image  |
| `application/src/main/resources/application.yaml`       | the database, the profile, and two override attempts whose fate the test reads                |
| `application/src/test/.../ApplicationSmokeTest.java`    | the discovery test: every declared workflow module has to be one VanillaBP wired              |
| `application/src/test/.../ConfigurationLevelsIT.java`   | which of the three configuration levels arrives                                               |
| `loan-approval/pom.xml`                                 | a foreign JAR's POM: own group, own version, own build, no parent                             |
| `loan-approval/src/main/java/com/acme/loanapproval/...` | the module's code, in the package its team chose                                              |
| `loan-approval/src/main/resources/loan-approval/...`    | the module's BPMN files and its own configuration, below its module id                        |
| `loan-approval/target/classes/META-INF/jandex.idx`      | the index the module's build writes; what makes it usable without this application knowing it |
| `loan-approval/src/test/java/com/acme/...`              | the module's own test, owned by the module's team and running without this application        |

What happens while the application is built: the index of every JAR is read, so the beans of
both modules become known without a line of configuration. At startup VanillaBP finds
the two `META-INF/workflow-module` markers, deploys the BPMN files each JAR brought, and
creates one `ProcessService` per aggregate. The application contributed the database, the
adapter and the environment - nothing else, and nothing module-specific.

Adding a third module is one dependency and one version. Removing one is deleting both lines.
That is the whole promise of this blueprint, and the smoke test is what keeps it honest.

## Documentation

- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
- [Defining a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#defining-a-workflow-module): the marker file, the resource conventions and the module's own configuration files
- [Provide an index for workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#provide-index-for-workflow-modules): why a JAR of its own needs one, and when the marker file alone is enough
- [Publishing a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#publishing-a-workflow-module): what a module consumed by a foreign application needs
- [How name clashes are avoided](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided): the three modes, and why changing the mode is a migration
- [Configuration of a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#configuration): the file names, the profiles and the priority order
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: how a BPMN task has to be modelled for that engine

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints). This repository is a
read-only mirror, **issues and pull requests belong there.**

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the
intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0

        https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the License.
