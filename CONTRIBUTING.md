# Contributing

Thanks for improving the VanillaBP blueprints. This page describes the rules a blueprint has
to follow. The rationale behind them is always the same: a blueprint is read more often than
it is run, and it is read by developers *and* by AI agents.

## Prerequisites

- JDK 21
- Git; Maven is provided by the wrapper (`./mvnw`)

## Where changes go

Issues and pull requests are handled in this repository. The per-blueprint repositories
below `vanillabp-blueprints` are mirrors written by CI, so changes pushed there are lost.

## Rule 1: every blueprint builds on its own

A blueprint is split into a repository of its own and cloned from there. Its POM therefore
must not depend on anything that only exists in this monorepo:

- **Do not** use the root POM as a Maven parent. Blueprints use the parent their platform
  needs (Spring Boot's `spring-boot-starter-parent`, Quarkus' BOM import).
- **Do not** rely on properties, dependency management or plugin management declared here.
- Declare versions, the VanillaBP BOM import and the BPMS profiles in the blueprint's own
  POM.
- Give the blueprint a groupId of its own, `io.vanillabp.blueprint.<blueprint-id>-<platform>`,
  which is the name of its repository. Every blueprint names its Maven modules after the same
  use case, and the two platform twins name them identically, so without the platform in the
  groupId the aggregator sees two modules called `loan-approval` and refuses to build.

The price is duplication between blueprints, and it is paid on purpose: a reader must be
able to understand a blueprint from the files in front of them. CI keeps the copies from
drifting apart.

The root POM aggregates the blueprints (`<modules>`) and checks formatting for the whole
repository, nothing else.

## Rule 2: a blueprint shows one aspect

`module-single` is the base blueprint; all `bpmn-*` blueprints share its structure and only
add their own aspect. Keep the delta small and make the `README.md` explain the delta rather
than the whole application.

Use the same package and file structure everywhere, so that understanding one blueprint means
understanding all of them:

```
<base-package>.<usecase>
├── ApiController.java               <- driving adapter: HTTP calls in
├── Service.java                     <- business code, never touches VanillaBP
├── Workflow.java                    <- outgoing: the application tells the process
├── WorkflowTaskHandler.java         <- incoming: the process tells the application
├── config/<UseCase>Properties.java
└── model/
    ├── Aggregate.java
    └── AggregateRepository.java
```

Talking to a BPMS happens in both directions, and each direction gets its own class:

```
ApiController ──────────┐
                        ├──→ Service ──→ Workflow ──→ ProcessService     outgoing
BPMS ──→ WorkflowTaskHandler ──┘                                         incoming
```

`Workflow` is the only place `ProcessService` is injected; `WorkflowTaskHandler` carries
`@WorkflowService` and every `@WorkflowTask` method and calls `Service`. Merging the two
would make the class depend on `Service` while `Service` depends on it, a circular bean
reference Spring Boot rejects at startup unless worked around with `@Lazy`. An interface does
not help, because the cycle is between beans, not types.

All four classes exist even where a blueprint only forwards through them, as in
`module-single`. Blueprints which need glue code, say for message correlation or asynchronous
tasks, then only add methods instead of restructuring, and that is what makes several deltas
composable.

The same applies to resources, and there it is not a matter of taste: workflow modules share
one classpath, so **all** resources of a module belong into one subdirectory named after the
workflow module ID. Only the marker file is outside it.

```
src/main/resources/
├── META-INF/workflow-module              <- contains 'loan-approval'
└── loan-approval/
    ├── loan-approval.yaml                <- the module's own configuration
    └── processes/<adapter-id>/*.bpmn     <- one directory per adapter id
```

Placeholders are identical in every blueprint, which is what makes them mechanically
replaceable:

|        Placeholder         |                                Meaning                                |
|----------------------------|-----------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                          |
| `loanapproval`             | use case identifier, Java package                                     |
| `loan-approval`            | use case identifier, kebab case (workflow module ID, resources, REST) |
| `loan_approval`            | BPMN process ID                                                       |

## Rule 3: usable in a browser, without tooling

Every blueprint must be operable with a browser alone:

1. The `README.md` documents **one** URL which starts the process.
2. At every wait state the handler logs the URLs to continue with: one fully populated,
   clickable URL per possible continuation.
3. No `curl`, no request body, no tool beyond the browser.

The API therefore consists of GET requests only. Besides being convenient for humans, the
logged URLs describe the reached process state in a way an agent can read.

## Rule 4: no BPMS specifics

Blueprints only cover what `spi-for-java` and `adapter-platform-integration` provide.
Anything specific to a BPMS belongs into that adapter's wiki. This is possible because
switching the BPMS is a Maven profile, not a code change.

Reference documentation is linked, never copied.

### Configuration follows the same rule

`application.yaml` holds what every engine needs. Everything belonging to one engine lives in
the profile file of that engine, `application-camunda7.yaml` and `application-camunda8.yaml`,
in the application module and, where a test needs it, in the workflow module's test resources.
That is how projects do it, and it is what makes a blueprint a starting point for a migration:
run with both profiles and both adapters, and the two configurations sit side by side instead
of being edited into each other.

**The profile is named once, on the Maven command line.** Every BPMS profile sets the property
`bpms`, and that property reaches the application twice over:

- the build filters it into `application.yaml`, which activates the profile of that name
  (`spring.profiles.active` respectively `quarkus.config.profile.parent`, where the BPMS
  profile becomes the parent of whichever profile the application runs in),
- surefire and failsafe hand it to the tests as a system property.

So `mvn -Pcamunda8 install verify` and `mvn -Pcamunda8 -pl application spring-boot:run` need
nothing else, and nobody has to keep two flags in sync. On Quarkus the filtering is declared in
the application module's POM, restricted to `application*.yaml` and to the `@...@` delimiters,
because Quarkus does not filter resources by default and `${...}` is its own expression syntax.

A BPMS-specific file is always shipped and only sometimes loaded: `application-camunda7.yaml`
sits in the JAR of a Camunda 8 build as well, where it does nothing. Naming an adapter id
whose adapter is not on the classpath is a configuration error VanillaBP refuses to start
with, and the profiles are what keeps that from happening.

## Rule 5: the aspect is proven by a test

A blueprint ships an integration test which plays through the aspect it shows, using a
simulator for surrounding systems, plus a smoke test of the application. That test is the
verification loop for generated code; without it, a blueprint cannot be used by an agent.

The workflow module is tested by running it. What that takes differs by platform: on Spring
Boot the test sources bring a minimal application (`TestApplication`) along, on Quarkus the
module is booted as the application under test and needs nothing but a database in
`src/test/resources/application.yaml`. Assert on the
**workflow aggregate**, never on the engine, because the aggregate is the only state that
means the same on every BPMS. And **wait** instead of asserting immediately, since a remote
BPMS gets to a task eventually.

## The test harness

What every blueprint needs for that is the same, so it is written once and copied:

```
templates/test-harness/<platform>/
├── workflow-module/         -> <maven-module>/src/test/java/blueprint/workflowmodule/
│   ├── TestApplication.java     boots the workflow module for its test (Spring Boot only)
│   ├── WorkflowModuleTest.java  base class: waiting for a workflow to make progress
│   └── Simulator.java           base class of a stand-in for a surrounding system
└── application/             -> <maven-module>/src/test/java/blueprint/workflowmodule/
    └── ApplicationSmokeTest.java
```

The two platforms have a set each, and the sets differ where the platform does: a Quarkus
workflow module needs no application class to be booted, and every Quarkus test class carries
`@QuarkusTest` itself rather than inheriting it from the base class.

Copies rather than a shared module, because a blueprint has to build standalone after the
split and an agent has to be able to read the code that verifies its work.

```bash
python3 bin/sync_harness.py            # copy the reference over every existing copy
python3 bin/sync_harness.py --check    # what CI does
```

**Never edit a copy.** Change the reference and run the sync. The script only refreshes
copies which already exist; putting the first one into a new blueprint is part of creating
it, and a blueprint takes only the files it needs (`Simulator.java` only if it has a
surrounding system to simulate).

Everything else in a blueprint's tests is specific to it and is never compared: the
integration test *is* the proof of the aspect and is supposed to differ.

## The platform twins

A blueprint exists once per platform, and the two are supposed to say the same thing. What
differs between them is the platform: its bean and injection annotations, its HTTP
annotations, how configuration is bound, how a repository looks. What must not differ is the
application: the BPMN wiring, the business methods, what the test asserts.

There is one exception, and its bar is the platform rather than the calendar: a blueprint
whose subject a platform does not know cannot exist there, and then it exists once. The
index says so with `status: not-applicable` and a one-sentence reason, which the
organisation page shows where the other platform shows its link. A blueprint nobody has
ported yet is a different thing and stays `planned` - that gap closes, this one does not.
`persistence-active-record` is the first of the kind: the idiom exists on one platform only.

`bin/check_twin_diff.py` keeps both honest: a blueprint with a single directory fails unless
the index declares the missing platform not applicable, so leaving a twin out is a decision
somebody writes down rather than something a guard quietly skips. Pass the index to have
that checked; without it, a single directory is only reported.

Where both twins are there, it compares their Java sources after removing package lines,
imports and comments, so a different import or a Javadoc written for one platform is never a
difference. Everything left over has to be approved in
`<group>/<blueprint-id>/twin-diff-allow.txt`, one line per file:

```
<hash>  <kind>  <path>  # why the twins differ here
```

```bash
python3 bin/check_twin_diff.py blueprints.yaml   # what CI does
python3 bin/check_twin_diff.py --update          # write the entries, keeping the reasons given
```

The hash covers the difference, not the file. Changing both twins the same way keeps it, so
ordinary work does not touch this file; changing one twin alone changes the hash and asks for
the approval again. That is the whole point: a difference is either the platform, and then it
has a reason written down, or it is a mistake that would otherwise spread to every blueprint
copied from this one.

Write the reason in terms of the platform, not of the code. "bean and injection annotations
only" says what a reviewer needs; "uses @Inject" does not.

## The picture of the process

Every blueprint README shows its process in the first section, before a word is said about
it. A BPMN file is not something a reader reads, and what a blueprint is about is usually
visible in the model.

The pictures are rendered from the BPMN files and committed, so a README renders on GitHub
without any tooling:

```bash
npm install -g bpmn-to-image     # once
bin/render_bpmn_images.sh
```

The result goes to `<group>/<blueprint-id>/<platform>/docs/<process-id>.png` and is referenced
relatively (`docs/loan_approval.png`), which keeps working after the split. Re-run the
script whenever a model changes, and commit what it wrote.

One picture covers every BPMS: the BPMN files below `processes/<adapter-id>/` differ in
engine specific attributes only, never in the diagram, so the script renders the models of
the first adapter directory it finds. PNG rather than SVG, because bpmn-js draws dark
strokes on no background at all and an SVG of that is unreadable in GitHub's dark mode.

## Adding a blueprint

1. Create `<group>/<blueprint-id>/<platform>/` and add it to `<modules>` of the root POM.
   The group is the blueprint's category: `blueprints-modules`, `blueprints-persistence`,
   `blueprints-bpmn` or `showcases`. `bin/check_index_consistency.py` rejects a blueprint
   sitting in the wrong one.
2. Copy the structure of `blueprints-modules/module-single/<platform>/` and apply your delta, including the
   test harness files it needs (see above) and the three files every split repository
   needs of its own:

   ```bash
   cp LICENSE NOTICE .gitignore <group>/<blueprint-id>/<platform>/
   ```
3. Write `README.md` (for humans) and `AGENTS.md` (for agents) from the templates, and
   render the picture of the process the README shows (see above):

   ```bash
   bin/render_bpmn_images.sh
   ```
4. Add the entry to `blueprints.yaml` of the `.github` repository. CI flips
   `platforms.<platform>.status` to `available` once the blueprint has been split out. A
   platform the blueprint cannot exist for is written by hand instead, as
   `not-applicable` plus a one-sentence `reason` (see above); no job derives that.
5. `./mvnw install verify` for every BPMS profile the blueprint supports. Only `camunda7`
   runs without infrastructure, because it is embedded. `camunda8` needs a cluster, which
   `bin/camunda8_cluster.sh start` gives you (see below), and is therefore not part of the
   default build.

The index and this repository have to agree: every blueprint directory is a module of the
root POM and has an index entry, and no index entry claims a blueprint which is not here.
CI checks that on every push; to check it locally:

```bash
curl -fsSLO https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/blueprints.yaml
python3 bin/check_index_consistency.py blueprints.yaml
```

## Delivery: how a blueprint reaches its own repository

Pushing to `main` runs `.github/workflows/split.yaml`, which calls
`bin/split_blueprints.sh`. For every directory `<group>/<blueprint-id>/<platform>/` that exists it

1. creates `vanillabp-blueprints/<blueprint-id>-<platform>` unless it is already there, and
   sets its description and homepage from `blueprints.yaml`,
2. runs `git subtree split` and force-pushes the result onto the mirror's `main`,
3. sets `platforms.<platform>.status` in the index to `available`.

What ends up in the mirror is the directory and nothing else, so everything a repository
needs has to be inside it: `LICENSE`, `NOTICE` and `.gitignore` are copies of the ones at
the root, and `bin/check_docs_structure.py` fails the build when a blueprint is missing one
or has let it drift.

The job is **directory driven**: nothing is created on stock. A blueprint appears in the
catalogue as `available` when, and only when, its directory exists and has been pushed out,
so the index can never advertise a repository which is not there. A platform that arrives
later is picked up by the next run without anybody having to remember it.

Force-pushing is safe because a mirror never carries commits of its own; it is derived from
this repository. That is also why the mirrors are read-only and every blueprint README says
where issues belong, a link the documentation check enforces.

To see what a run would do, without creating or pushing anything:

```bash
DRY_RUN=1 bin/split_blueprints.sh
```

The job needs the repository secret `BLUEPRINTS_SPLIT_TOKEN`, a fine-grained token for the
organisation with `Administration: read and write` and `Contents: read and write`. The
`GITHUB_TOKEN` of the workflow cannot be used: it is scoped to this repository and can
neither create the mirrors nor push into them.

## Versions

Blueprints target the versions below. Since they are repeated in every blueprint POM, this
table is the place to look up what is current.

|                                                               |    Version     |
|---------------------------------------------------------------|----------------|
| Java                                                          | 21             |
| Spring Boot                                                   | 4.1.0          |
| Quarkus                                                       | 3.37.1         |
| `io.vanillabp:vanillabp-bom`                                  | 2.0.0-SNAPSHOT |
| `org.camunda.community.vanillabp:camunda7-adapter-<platform>` | 2.0.0-SNAPSHOT |
| `org.camunda.community.vanillabp:camunda8-adapter-<platform>` | 2.0.0-SNAPSHOT |
| `io.vanillabp:process-engine-api-adapter-<platform>`          | 2.0.0-SNAPSHOT |

VanillaBP artifacts are version-managed by `io.vanillabp:vanillabp-bom`; BPMS adapters are
released independently and carry their own version.

## What CI builds

Three workflows, and they answer three different questions.

|    Workflow    |                                            Question                                             |
|----------------|-------------------------------------------------------------------------------------------------|
| `checks.yaml`  | do index, documentation structure, test harness copies and platform twins agree?                |
| `build.yaml`   | does every blueprint build and test, alone and through the aggregator, on every BPMS it claims? |
| `nightly.yaml` | does it still, against today's snapshots of the framework?                                      |

The job that matters most is `blueprint`: it builds `<group>/<blueprint-id>/<platform>/` with a
plain `mvn`, without the aggregator, the wrapper or the root POM. That is exactly the
repository a user clones after the split, so a blueprint does not need to be split to find
out whether it works. The matrix is not maintained anywhere; it is the list of directories
that exist, the same rule the split job follows, crossed with the `bpms` list of each
blueprint in the index (`bin/build_matrix.py`). A blueprint which cannot run on an engine
says so once, in its index entry, next to everything else it declares - and a directory the
index does not know yet is built against every engine, because being unknown is a finding of
its own rather than a reason to test less.

Camunda 7 is embedded and needs nothing. Camunda 8 is remote, so the job starts a cluster
first:

```bash
bin/camunda8_cluster.sh start
cd blueprints-bpmn/bpmn-service-task/springboot && mvn -Pcamunda8 clean install verify
bin/camunda8_cluster.sh stop
```

`clean` matters when you switch the profile in a tree you have already built. The output of
the previous profile stays in `target/`, and the build then runs against the adapter of that
profile while the command line names the other one. VanillaBP reports it correctly, but the
message is about a missing address or an environment variable addressing an unknown adapter,
and it takes a while to connect that to a directory nobody cleaned. CI never sees this - it
starts from a fresh checkout.

The address reaches the blueprint as an environment variable binding to
`vanillabp.adapters.camunda8.rest-address`. Both spellings are set, because the platforms
read the name differently: Spring Boot binds `VANILLABP_ADAPTERS_CAMUNDA8_RESTADDRESS`,
Quarkus expects the dash as an underscore
(`VANILLABP_ADAPTERS_CAMUNDA8_REST_ADDRESS`), and a variable matching no property is
ignored. No CI-specific address is checked in anywhere.

### Reading the VanillaBP snapshots

Until 2.0.0 is released to Maven Central, the framework repositories publish their
snapshots to GitHub Packages, which requires a token even for public packages.
`.github/workflows/github-packages-settings.xml` names the registries; the credentials come
from the repository secrets `VANILLABP_USER_NAME` and `VANILLABP_USER_TOKEN`, a token
carrying `read:packages`. Locally, the same settings file works with those two variables
exported. The blueprint POMs stay free of all this: a blueprint shows what an application
needs, and after the release that is Maven Central.

## Dependency updates

Renovate opens the pull requests (`renovate.json`) and merges them itself once the checks
above are green. Since every blueprint POM repeats the same versions, an upgrade is one PR
touching all of them, never one PR per blueprint.

Two exceptions are deliberate:

- VanillaBP and the BPMS adapters are excluded. Their versions follow the framework and are
  bumped when a release is cut.
- Spring Boot and Quarkus are not merged automatically. Their versions are also written in
  the table below, and a bot cannot carry them there.

Automerge relies on the checks being required for the branch. Without branch protection
naming them, Renovate merges as soon as GitHub lets it, which is not what anybody wants.

Require the job `verified` and not the matrix jobs. The matrix is generated from the
directories that exist, so its job names change with every blueprint added, and a required
check nobody produces any more blocks every pull request. `verified` waits for all of them
and keeps its name.

## Writing style

Blueprints are read more often than they are run, so the prose matters as much as the code.
Write plainly: no em dashes, no bullet lists whose items start with a bolded label, no
groups of three for the sake of rhythm, no phrases about how significant something is. Say
what a thing is rather than what it "serves as", and drop hedging and filler. The same
applies to Javadoc, code comments and log messages, which are documentation as well.

## Formatting

Spotless runs during `process-sources` and fails the build on violations:

```bash
./mvnw -N spotless:apply
```

The goal is invoked non-recursively (`-N`) because Spotless is declared in the root POM
only. A blueprint's POM shows what a VanillaBP application needs and no build tooling of
ours.

Java is formatted by the Eclipse JDT formatter configured in `formatting_conventions.xml`
(2-space indentation, import order `java,javax,org,com,at.phactum`, one line per method call
in fluent APIs of more than one call). POMs are sorted, Markdown is formatted as well.

Two kinds of YAML are excluded, both because the formatter drops comments: GitHub workflow
files, where it also unquotes version numbers and turns `"3.10"` into `3.1`, and everything
below a blueprint's `src/`, where the comments are what is being taught.

## License

By contributing you agree that your contribution is licensed under the
[Apache License, Version 2.0](LICENSE).
