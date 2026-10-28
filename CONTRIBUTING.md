# Contributing

Thanks for improving the VanillaBP blueprints. This page describes the rules a blueprint has to
follow. The reason behind them is always the same: people read a blueprint more often than they
run it, and both developers *and* AI agents read it.

## From a bug to a pull request

Found a bug? You can hand it to a coding agent.

1. Clone [`development-workspace`](https://github.com/vanillabp/development-workspace) with
   `git clone --recurse-submodules`. It holds the VanillaBP repositories side by side. Its
   `.claude/skills` folder holds the skills Claude Code reads when it starts at the root of the
   workspace.
2. Start your coding agent at the root of the workspace and describe the bug: what you did, what
   you expected and what happened instead.
3. The agent builds a scenario which reproduces the bug, looks for the cause and fixes it. Then it
   pushes a branch to your fork and opens a pull request from there.
4. The VanillaBP team reviews the pull request and merges it.

You need a GitHub account, the GitHub CLI logged in with `gh auth login`, a fork of the repository
you change (the agent can create it with `gh repo fork`), and Java 21, Maven and Docker for the
build. The submodules are cloned over SSH. If GitHub has no SSH key of yours, run
`git config --global url."https://github.com/".insteadOf "git@github.com:"` before you clone. If
the bug is in a repository the workspace does not hold, the agent clones it next to the others.
Before it opens the pull request, the agent follows the `CONTRIBUTING.md` and the `AGENTS.md` of
the repository it changes, where it has one. The two Camunda adapters live in the Camunda Community
Hub, which asks you to sign its contributor license agreement on your first pull request there.

## Prerequisites

- JDK 21 or newer; the workflows use 25, and every blueprint compiles for Java 21
- Git; Maven is provided by the wrapper (`./mvnw`)

## Where changes go

Issues and pull requests are handled in this repository. The repositories of the single
blueprints below `vanillabp-blueprints` are mirrors which CI writes. Changes pushed there are
lost.

## Rule 1: every blueprint builds on its own

A blueprint is split into a repository of its own, and people clone it from there. So its POM
must not depend on anything which only exists in this monorepo:

- **Do not** use the root POM as a Maven parent. Blueprints use the parent their platform needs
  (Spring Boot's `spring-boot-starter-parent`, Quarkus' BOM import).
- **Do not** rely on properties, dependency management or plugin management declared here.
- Declare versions, the VanillaBP BOM import and the BPMS (business process management system)
  profiles in the blueprint's own POM.
- Give the blueprint a groupId of its own, `io.vanillabp.blueprint.<blueprint-id>-<platform>`,
  which is the name of its repository. Every blueprint names its Maven modules after the same
  use case, and the two platform twins give them the same names. Without the platform in the
  groupId, the aggregator sees two modules called `loan-approval` and refuses to build.

The price is duplication between blueprints, and we pay it on purpose. A reader must be able to
understand a blueprint from the files in front of them. CI keeps the copies from drifting
apart.

The root POM collects the blueprints (`<modules>`) and checks the formatting of the whole
repository. It does nothing else.

## Rule 2: a blueprint shows one aspect

`module-single` is the base blueprint. All `bpmn-*` blueprints share its structure and only add
their own aspect. Keep the delta small. Let the `README.md` explain the delta, not the whole
application.

Use the same package and file structure everywhere. Then understanding one blueprint means
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

Talking to a BPMS goes in both directions, and each direction gets its own class:

```
ApiController ──────────┐
                        ├──→ Service ──→ Workflow ──→ ProcessService     outgoing
BPMS ──→ WorkflowTaskHandler ──┘                                         incoming
```

`Workflow` is the only place where `ProcessService` is injected. `WorkflowTaskHandler` carries
`@WorkflowService` and every `@WorkflowTask` method, and it calls `Service`. If you merged the
two, the class would depend on `Service` while `Service` depends on it. Spring Boot rejects
such a circular bean reference at startup, unless you work around it with `@Lazy`. An interface
does not help, because the cycle is between beans, not types.

All four classes exist even where a blueprint only forwards through them, as in
`module-single`. Blueprints which need glue code, for example for message correlation or
asynchronous tasks, then only add methods. They do not have to restructure. That is why several
deltas can be combined.

The same applies to resources, and there it is not a matter of taste. Workflow modules share
one classpath. So **all** resources of a module belong in one subdirectory named after the
workflow module ID. Only the marker file is outside it.

```
src/main/resources/
├── META-INF/workflow-module              <- contains 'loan-approval'
└── loan-approval/
    ├── loan-approval.yaml                <- the module's own configuration
    └── processes/<adapter-id>/*.bpmn     <- one directory per adapter id
```

Placeholders are the same in every blueprint, so a tool can replace them:

|        Placeholder         |                                Meaning                                |
|----------------------------|-----------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                          |
| `loanapproval`             | use case identifier, Java package                                     |
| `loan-approval`            | use case identifier, kebab case (workflow module ID, resources, REST) |
| `loan_approval`            | BPMN process ID                                                       |

Inside the classes, an injected bean is named after the role it plays there, not after its
type. A call then reads like a short sentence about the business case:

```java
// WorkflowTaskHandler.java
@Autowired
private Service loanApproval;

@WorkflowTask
public void retrieveCreditRating(final Aggregate loanRequest) {
  loanApproval.assessCreditRating(loanRequest);
}

// Workflow.java
@Autowired
private ProcessService<Aggregate> bpms;

public void loanRequested(final Aggregate loanApproval) {
  bpms.startWorkflow(loanApproval);
}
```

The same names are used in every blueprint:

|           Bean           |                  Name                   |
|--------------------------|-----------------------------------------|
| the use case's `Service` | the use case, singular: `loanApproval`  |
| `ProcessService`         | `bpms`                                  |
| `Workflow`               | `workflow`                              |
| the aggregate repository | what it stores, plural: `loanApprovals` |

A blueprint with another use case takes its own word, such as `rateWatch` or `nightlyReview`.
Where a parameter or a local variable would hide the field, give it a name from the domain. The
aggregate of a loan approval is `loanRequest`. Do not use `request` or `application` alone,
because both words already mean something else in IT. If a method name would repeat the field,
drop that part: `loanApproval.request(...)` and `loanApproval.get(id)`, not
`loanApproval.initiateLoanApproval(...)` and `loanApproval.getLoanApproval(id)`.

## Rule 3: usable in a browser, without tooling

You must be able to operate every blueprint with a browser alone:

1. The `README.md` documents **one** URL which starts the process.
2. At every wait state the handler logs the URLs to continue with: one complete, clickable URL
   for each possible next step.
3. No `curl`, no request body, no tool beyond the browser.

So the API has GET requests only. The logged URLs are convenient for humans. They also describe
the state the process reached in a way an agent can read.

## Rule 4: no BPMS specifics

Blueprints only cover what `spi-for-java` and `adapter-platform-integration` provide. Anything
specific to a BPMS belongs in that adapter's wiki. This works because switching the BPMS is a
Maven profile, not a code change.

Reference documentation is linked, never copied.

The documentation of this repository follows the same rule. A blueprint shows that an
application needs no knowledge of the BPMS. A `README.md` which explains a BPMS says the
opposite. So the files here name a BPMS where a command needs the name, and nowhere else.
Everything a reader wants to know about one is in that adapter's own documentation.

### Configuration follows the same rule

`application.yaml` holds what every engine needs. Everything which belongs to one engine is in
the profile file of that engine, `application-<bpms>.yaml`. That file is in the application
module and, where a test needs it, in the workflow module's test resources. That is how
projects do it. It also makes a blueprint a starting point for a migration. Run with either
profile and either adapter, and the two configurations sit side by side. Nobody has to edit one
into the other.

**The profile is named once, on the Maven command line.** Every BPMS profile sets the property
`bpms`, and that property reaches the application in two ways:

- the build filters it into `application.yaml`, which activates the profile of that name
  (`spring.profiles.active` or `quarkus.config.profile.parent`, where the BPMS profile becomes
  the parent of the profile the application runs in),
- surefire and failsafe hand it to the tests as a system property.

So `mvn -P<bpms> install verify` and `mvn -P<bpms> -pl application spring-boot:run` need
nothing else, and nobody has to keep two flags in sync. On Quarkus the filtering is declared in
the application module's POM. It is limited to `application*.yaml` and to the `@...@`
delimiters. Quarkus does not filter resources by default, and `${...}` is its own expression
syntax.

A file for one BPMS is always shipped and only sometimes loaded. The file of one engine is also
in the JAR of a build for another one, where it does nothing. If you name an adapter id whose
adapter is not on the classpath, VanillaBP refuses to start, because that is a configuration
error. The profiles keep that from happening.

One setting has to say the same thing in every profile which uses an adapter:
`name-clash-avoidance`. It decides what the adapter calls a process, a message, a signal and an
error in the BPMS. So changing it while an application runs is a migration, not a setting.
Workflows started before would no longer be found. Leaving it out of one profile is enough to
change it, because then the default `by-adapter` applies there. The tests are no exception. A
module test runs under the BPMS profile of the build, but it reads the profile files of its own
source root. A test root without `application-<bpms>.yaml` runs that profile under the default.
Then it tests a configuration the blueprint does not ship. So a test root has a profile file
for every BPMS whose application profile names a mode. `bin/check_scoping_modes.py` compares
every profile of every source root of a blueprint, main and test, and fails on a difference:

```bash
python3 bin/check_scoping_modes.py
```

It found `module-bpms-migration`, whose `camunda7` profile set `use-prefix` while its
`camunda8` profile named nothing. So the migration the blueprint shows lost the message of
every loan approval started before the switch. Later it found 42 test source roots which ran
`camunda7` under the default, while their applications ran `use-prefix`.
`module-bpms-migration` was one of them. A blueprint which really wants to show a change of
mode shows it the way the wiki does: as a migration with a second adapter id.

The script also compares the two twins of a blueprint. Each twin can agree with itself and
still run another mode than the other twin. The Quarkus twins of the `bpmn-*` blueprints did
that: they had no `application-camunda7.yaml`, so Quarkus ran Camunda 7 under the default while
Spring Boot ran `use-prefix`. An adapter which one twin uses and the other names nowhere counts
there under the default, if that twin has a Maven profile of the same name.

A second setting is written out in every `camunda8` profile, for a different reason:
`job-lease`. The Camunda 8 adapter has no default for it. On a cluster of the 8.10 line it
stops the boot while the key is missing. With a lease, the cluster only takes the answer of the
current activation. So a run whose lock ran out while it was working can no longer overwrite
what the newer run wrote. The blueprints write `use`. The adapter recommends that value, and
its own example shows it, and a blueprint shows the recommended way. The other value,
`do-not-use`, is what every release line before 8.10 did. An application which may have to move
back to such a line needs it, because the cluster never takes a lease off a job again. What a
lease costs is in the
[wiki of the adapter](https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/wiki/Configuration#a-job-somebody-else-holds).

## Rule 5: the aspect is proven by a test

A blueprint ships an integration test which plays through the aspect it shows, with a simulator
for surrounding systems. It also ships a smoke test of the application. An agent uses that test
to check the code it generated. Without it, an agent cannot use a blueprint.

The workflow module is tested by running it. What that takes differs by platform. On Spring
Boot the test sources bring a minimal application (`TestApplication`) along. On Quarkus the
module is booted as the application under test, and needs nothing but a database in
`src/test/resources/application.yaml`. Assert on the **workflow aggregate**, never on the
engine. The aggregate is the only state which means the same on every BPMS. And **wait**
instead of asserting at once, because a remote BPMS gets to a task some time later.

### The image a test pins has to start everywhere

A test which starts a container names the version of the image. So a run says what it ran
against, and a new release cannot change the result overnight. The version has to start
everywhere the blueprint is built, and that is more than the CI runner. MongoDB 8.0 taught us
that. It refuses to start on a Linux kernel 6.19 or newer
([SERVER-121912](https://jira.mongodb.org/browse/SERVER-121912)) and ends with exit code 1. So
`persistence-mongodb` failed on a current machine, while CI stayed green on its older kernel.
The blueprint now runs `mongo:8.2`.

Read the version from a system property with the pinned one as its default:

```java
private static final String IMAGE = System.getProperty("mongodb.image", "mongo:8.2");
```

Then the next machine which needs another version passes `-Dmongodb.image=mongo:7.0`, and the
blueprint stays as it is. Where a platform starts the container for you, as Quarkus does with
its dev services, let it. The version then comes with the platform and moves with it.

## The test harness

Every blueprint needs the same for that. So it is written once and copied:

```
templates/test-harness/<platform>/
├── workflow-module/         -> <maven-module>/src/test/java/blueprint/workflowmodule/
│   ├── TestApplication.java     boots the workflow module for its test (Spring Boot only)
│   ├── WorkflowModuleTest.java  base class: waiting for a workflow to make progress
│   └── Simulator.java           base class of a stand-in for a surrounding system
└── application/             -> <maven-module>/src/test/java/blueprint/workflowmodule/
    └── ApplicationSmokeTest.java
```

The two platforms have a set each. The sets differ where the platform does. A Quarkus workflow
module needs no application class to be booted. And every Quarkus test class carries
`@QuarkusTest` itself, instead of inheriting it from the base class.

They are copies and not a shared module. A blueprint has to build on its own after the split.
And an agent has to be able to read the code which checks its work.

```bash
python3 bin/sync_harness.py            # copy the reference over every existing copy
python3 bin/sync_harness.py --check    # what CI does
```

**Never edit a copy.** Change the reference and run the sync. The script only refreshes copies
which already exist. Putting the first one into a new blueprint is part of creating it. A
blueprint takes only the files it needs (`Simulator.java` only if it has a surrounding system
to simulate).

Everything else in a blueprint's tests is specific to it, and nothing compares it. The
integration test *is* the proof of the aspect, and it is supposed to differ.

## The platform twins

A blueprint exists once per platform, and the two should say the same thing. The platform is
what differs between them: its bean and injection annotations, its HTTP annotations, how
configuration is bound, how a repository looks. The application must not differ: the BPMN
wiring, the business methods, what the test asserts.

There is one exception, and the platform decides it, not the calendar. A blueprint whose
subject a platform does not know cannot exist there. Then it exists once. The index says so
with `status: not-applicable` and a one-sentence reason. The organisation page shows the reason
where the other platform shows its link. A blueprint nobody has ported yet is a different
thing, and it stays `planned`. That gap closes, this one does not. `persistence-active-record`
is the first of this kind: the idiom exists on one platform only.

`bin/check_twin_diff.py` keeps both honest. A blueprint with a single directory fails, unless
the index declares the missing platform not applicable. So leaving a twin out is a decision
somebody writes down, not something a check quietly skips. Pass the index to have that checked.
Without it, a single directory is only reported.

Where both twins are there, it compares their Java sources after removing package lines,
imports and comments. So a different import or a Javadoc written for one platform is never a
difference. Everything left over has to be approved in
`<group>/<blueprint-id>/twin-diff-allow.txt`, one line per file:

```
<hash>  <kind>  <path>  # why the twins differ here
```

```bash
python3 bin/check_twin_diff.py blueprints.yaml   # what CI does
python3 bin/check_twin_diff.py --update          # write the entries, keeping the reasons given
```

The hash covers the difference, not the file. If you change both twins the same way, it stays.
So ordinary work does not touch this file. If you change one twin alone, the hash changes and
asks for the approval again. That is the whole point. Either a difference comes from the
platform, and then it has a reason written down. Or it is a mistake, which would otherwise
spread to every blueprint copied from this one.

Write the reason in terms of the platform, not of the code. "bean and injection annotations
only" says what a reviewer needs. "uses @Inject" does not.

## The file tables

People read a blueprint through its tables: the core files and the boilerplate files in
`AGENTS.md`, the delta to the base blueprint and "How it works" in `README.md`. A reader looks
at them first, and they go out of date first, because moving a file does not touch them. Story
683 corrected 22 blueprints which still named a profile file which had moved a month earlier.

`bin/check_table_paths.py` reads every table whose first column is `File`. It checks each path
in a row against the file system:

```bash
python3 bin/check_table_paths.py
```

A path may start at the root of the blueprint (`loan-approval/pom.xml`). It may use `...` for
the part the writer left out, or `<...>` for a name the reader chooses. A README names a file
the way its section reads it, `Workflow.java` and not the whole path. So a path which leads
nowhere from the root is looked for below it.

A first cell which is not a file needs no exception of its own, because the header of the table
decides. A table of beans, of BPMN element ids or of placeholders is headed `Bean`, `Name` or
`Placeholder`, and the script does not read it. A row which says the file is `deleted` or
`gone` is read the other way round. It fails when the blueprint still has the file.

## The picture of the process

Every blueprint README shows its process in the first section, before a word is said about it.
Nobody reads a BPMN file, and the model usually shows what a blueprint is about.

The pictures are rendered from the BPMN files and committed. So a README renders on GitHub
without any tooling:

```bash
npm install -g bpmn-to-image     # once
bin/render_bpmn_images.sh
```

The result goes to `<group>/<blueprint-id>/<platform>/docs/<process-id>.png`, and the README
links it with a relative path (`docs/loan_approval.png`). That keeps working after the split.
Run the script again whenever a model changes, and commit what it wrote.

One picture covers every BPMS. The BPMN files below `processes/<adapter-id>/` differ only in
attributes for the engine, never in the diagram. So the script renders the models of the first
adapter directory it finds. PNG and not SVG, because bpmn-js draws dark strokes on no
background at all. An SVG of that cannot be read in GitHub's dark mode.

## Adding a blueprint

1. Create `<group>/<blueprint-id>/<platform>/` and add it to `<modules>` of the root POM. The
   group is the blueprint's category: `blueprints-modules`, `blueprints-persistence`,
   `blueprints-bpmn` or `showcases`. `bin/check_index_consistency.py` rejects a blueprint in
   the wrong one.
2. Copy the structure of `blueprints-modules/module-single/<platform>/` and apply your delta.
   Include the test harness files it needs (see above), and the three files every split
   repository needs of its own:

   ```bash
   cp LICENSE NOTICE .gitignore <group>/<blueprint-id>/<platform>/
   ```

   A blueprint which runs on Camunda 7 runs on Operaton as well, the open source successor of
   Camunda 7. The script below adds the `operaton` profile next to every `camunda7` profile,
   the profile files and the copies of the BPMN files. It names what it left for you to do by
   hand:

   ```bash
   python3 bin/add_bpms_profile.py operaton <group>/<blueprint-id>/<platform>
   ```
3. Write `README.md` (for humans) and `AGENTS.md` (for agents) from the templates. Render the
   picture of the process which the README shows (see above):

   ```bash
   bin/render_bpmn_images.sh
   ```
4. Add the entry to `blueprints.yaml` of the `.github` repository. CI switches
   `platforms.<platform>.status` to `available` once the blueprint has been split out. For a
   platform the blueprint cannot exist for, write the status by hand instead: `not-applicable`
   plus a one-sentence `reason` (see above). No job derives that.
5. Run `./mvnw install verify` for every BPMS profile the blueprint declares. A BPMS which runs
   inside the application needs nothing else. One which runs as a server needs that server.
   `bin/camunda8_cluster.sh start` gives you one (see below), so it is not part of the default
   build.

The index and this repository have to agree. Every blueprint directory is a module of the root
POM and has an index entry. No index entry claims a blueprint which is not here. CI checks that
on every push. To check it locally:

```bash
curl -fsSLO https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/blueprints.yaml
python3 bin/check_index_consistency.py blueprints.yaml
```

## Delivery: how a blueprint reaches its own repository

A push to `main` runs `.github/workflows/split.yaml`, which calls `bin/split_blueprints.sh`.
For every directory `<group>/<blueprint-id>/<platform>/` which exists, it

1. creates `vanillabp-blueprints/<blueprint-id>-<platform>` unless it is already there, and
   sets its description and homepage from `blueprints.yaml`,
2. runs `git subtree split` and force-pushes the result onto the mirror's `main`,
3. sets `platforms.<platform>.status` in the index to `available`.

The mirror gets the directory and nothing else. So everything a repository needs has to be
inside it. `LICENSE`, `NOTICE` and `.gitignore` are copies of the ones at the root.
`bin/check_docs_structure.py` fails the build when a blueprint is missing one, or when its copy
differs.

The job is **driven by directories**. Nothing is created in advance. A blueprint appears in the
catalogue as `available` when, and only when, its directory exists and has been pushed out. So
the index can never advertise a repository which is not there. The next run picks up a platform
which arrives later, and nobody has to remember it.

Force-pushing is safe, because a mirror never has commits of its own. It is derived from this
repository. That is also why the mirrors are read-only, and why every blueprint README says
where issues belong. The documentation check makes sure that link is there.

To see what a run would do, without creating or pushing anything:

```bash
DRY_RUN=1 bin/split_blueprints.sh
```

The job needs the repository secret `BLUEPRINTS_SPLIT_TOKEN`, a fine-grained token for the
organisation with `Administration: read and write` and `Contents: read and write`. The
`GITHUB_TOKEN` of the workflow does not work. It is limited to this repository, so it can
neither create the mirrors nor push into them.

## Versions

Blueprints target the versions below. Every blueprint POM repeats them, so this table is the
place to look up what is current.

The two platforms name a line, not an exact version, because the patch inside the line moves on
its own. A Spring Boot or Quarkus patch merges itself once the build is green. A table which
had to be edited for that would be wrong more often than right. The exact version is in the
POMs. Moving the line itself is a pull request somebody reads, and that pull request carries
the new line into this table.

|                              |    Version     |
|------------------------------|----------------|
| Java                         | 21             |
| Spring Boot                  | 4.1.x          |
| Quarkus                      | 3.37.x         |
| `io.vanillabp:vanillabp-bom` | 2.0.0-SNAPSHOT |

`io.vanillabp:vanillabp-bom` manages the versions of the VanillaBP artifacts. A BPMS adapter is
released on its own and has its own version. So its coordinate is in the BPMS profile of every
blueprint POM, and that adapter documents its version. Neither is repeated here. A second place
to look up a version is a second place to have it wrong.

An adapter may carry a compatibility value inside its version. Where it does, the engine the CI
starts has to move with it. Renovate cannot cross that boundary on its own. `renovate.json`
extends the preset which that adapter ships, because that preset reads the value. The adapter
which carries the value documents what it means, for Camunda 8 under
[release lines](https://github.com/camunda-community-hub/vanillabp-camunda8-adapter#release-lines).

`bin/camunda8_cluster.sh` has no version of its own. It reads the adapter version from the
blueprint POMs, fetches the POM of that adapter and starts the Camunda version which that POM
names for `io.camunda:camunda-client-java`. This client is the lowest cluster version the adapter
accepts. So the cluster moves with the adapter. A pin used to be here, and it fell behind: it
said 8.10.0 while the adapter was built against 8.10.5. Set `CAMUNDA_VERSION` to run another
version on purpose.

## What CI builds

There are four workflows, and they answer four different questions.

|       Workflow       |                                            Question                                             |
|----------------------|-------------------------------------------------------------------------------------------------|
| `checks.yaml`        | do index, documentation, file tables, harness copies, platform twins and BPMS profiles agree?   |
| `build.yaml`         | does every blueprint build and test, alone and through the aggregator, on every BPMS it claims? |
| `nightly.yaml`       | does it still, against the platform snapshot as it is published today?                          |
| `nightly-issue.yaml` | does anybody hear about it when the night says no?                                              |

The job which matters most is `blueprint`. It builds `<group>/<blueprint-id>/<platform>/` with
a plain `mvn`, without the aggregator, the wrapper or the root POM. That is exactly the
repository a user clones after the split. So a blueprint does not need to be split to find out
whether it works. Nobody maintains the matrix. It is the list of directories which exist, the
same rule the split job follows, crossed with the `bpms` list of each blueprint in the index
(`bin/build_matrix.py`). A blueprint which cannot run on an engine says so once, in its index
entry, next to everything else it declares. A directory which the index does not know yet is
built against every engine. Being unknown is a finding of its own, not a reason to test less.

A BPMS which runs inside the application needs nothing. One which runs as a server has to be
started first. `bin/camunda8_cluster.sh` starts the one this repository builds against:

```bash
bin/camunda8_cluster.sh start
cd blueprints-bpmn/bpmn-service-task/springboot && mvn -Pcamunda8 clean install verify
bin/camunda8_cluster.sh stop
```

`clean` matters when you switch the profile in a tree you have already built. The output of the
previous profile stays in `target/`. The build then runs against the adapter of that profile,
while the command line names the other one. VanillaBP reports it correctly. But the message is
about a missing address, or about an environment variable for an unknown adapter. It takes a
while to connect that to a directory nobody cleaned. CI never sees this, because it starts from
a fresh checkout.

The address reaches the blueprint as an environment variable which binds to
`vanillabp.adapters.camunda8.rest-address`. Both spellings of the name are set, because the two
platforms read the dash in a key differently. That is the framework's rule, not this
repository's. The wiki writes it out for
[Spring Boot](https://github.com/vanillabp/adapter-platform-integration/wiki/Spring-Boot-integration#overriding-configuration-via-environment-variables)
and for
[Quarkus](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#overriding-configuration-via-environment-variables).
No CI-specific address is checked in anywhere.

### Reading the VanillaBP snapshots

Until 2.0.0 is released, `spi-for-java` and the platform publish their snapshots to the
snapshot repository of Maven Central, which needs no login. The two Camunda adapters publish
theirs to GitHub Packages, because their namespace belongs to the Camunda Community Hub. GitHub
Packages needs a token even for public packages.
`.github/workflows/github-packages-settings.xml` names all three places. The credentials come
from the repository secrets `VANILLABP_USER_NAME` and `VANILLABP_USER_TOKEN`, a token with
`read:packages`. Locally, the same settings file works with those two variables exported. The
blueprint POMs stay free of all this. A blueprint shows what an application needs, and after
the release that is Maven Central.

### Which platform a run built against

Every framework artifact is `2.0.0-SNAPSHOT`, and the framework publishes under that version
several times a day. So the version in a build log does not say which framework a run used. Two
runs of the same branch can mean different things.

`bin/platform_snapshot.sh` writes it down. `build.yaml` calls it in the aggregator job and in
every blueprint job. Each run then has a list like this in its summary:

```
- `io.vanillabp:vanillabp-bom` 2.0.0-20260926.082311-263
- `io.vanillabp:vanillabp-schema` 2.0.0-20260926.082311-203
```

The timestamp and the number after it name the build which the snapshot repository served. So
you can read two runs next to each other. The script only names what Maven fetched during the
run. If Maven refreshed nothing, the report says so. It does not pass off the runner's cache as
the framework of today.

Read it before you conclude anything from a green job next to a red one. On the 25th of
September 2026 seven blueprint jobs were green and one was red, and they looked comparable.
They were not. The green ones ran at 13:46. The framework published a new table at 14:39. The
red one was a second attempt of the same run, started the next morning. A re-run of the failed
jobs does not run the aggregator again. That is why every blueprint job reports for itself.

### When the night goes red

A pull request build sees what changes in this repository. The framework lives in other
repositories and is published again several times a day. So a pull request build cannot see a
platform change which breaks a blueprint. It has happened. A change in the Spring Boot
integration left two blueprints unable to compile, every pull request stayed green, and a
person found it a day later.

`nightly.yaml` is the build which reads the platform snapshot from where it is published. It
asks to run at 02:07 and at 12:07 UTC. It only asks, because GitHub puts a scheduled run into a
queue instead of starting it. The odd minute is because that queue is longest on the hour. The
early run also comes after the four platform repositories which measure the same published
snapshot, the last of them at 01:11. The midday run belongs to the time before the 2.0 release.
Several stories a day reach the platform now, and a break found at two in the morning arrives
together with everything merged since. After the release the midday run goes, and the early run
stays alone. The head of the workflow file says the same.

It starts with the job `platform`. That job stops the run when the two secrets are empty,
because sixty jobs which fail on a 401 look like sixty broken blueprints. It also writes down
which platform build the night got. No job of that run restores the Maven cache, and every
Maven call passes `--update-snapshots`. So nothing here can quietly build against yesterday's
framework.

Every job which went red attaches its surefire and failsafe reports to the run. The artifact is
called `test-reports-<blueprint>-<bpms>`, with the slashes of the blueprint path turned into
dashes. GitHub keeps it for thirty days, longer than the log of the job. The issue below spells
out the name of the first red job. So the failed test is one download away, and you do not have
to scroll through a log which is thrown away sooner.

A red night becomes a GitHub issue under the label `nightly`, written by `nightly-issue.yaml`.
There is one issue for the whole night. The first thing in it is the count of the blueprints
which broke. Many at once means the platform, one alone means that blueprint. While the issue
is open, every further red night is a comment on it, and so is the first night which is green
again. Whoever fixes the break closes the issue. Nothing closes it on its own.

This is the same build by hand. Run it when the issue names a blueprint. The issue also names
the BPMS, and that is the profile to pass:

```bash
export USER_NAME=<github user> USER_TOKEN=<token with read:packages>
cd <group>/<blueprint-id>/<platform>
mvn -s ../../../.github/workflows/github-packages-settings.xml \
  --update-snapshots -P<bpms> verify
```

## Dependency updates

Renovate opens the pull requests (`renovate.json`) at night, and merges them itself once the
checks above are green. Every blueprint POM repeats the same versions. So an upgrade is one
pull request which touches all of them, never one per blueprint. Only one is open at a time, so
the matrix does not compete with the branch somebody works on.

Two exceptions are on purpose:

- VanillaBP and the BPMS adapters are left out. Their versions follow the framework and are
  raised when a release is cut. Once an adapter which pins its engine is released, the preset
  it ships keeps an update inside the line the blueprints build against.
- Spring Boot and Quarkus are not merged automatically. Their versions are also written in the
  table under `Versions` above, and a bot cannot carry them there.

Automatic merging relies on the checks being required for the branch. Without branch protection
which names them, Renovate merges as soon as GitHub lets it. Nobody wants that.

Require the job `verified`, not the matrix jobs. The matrix is generated from the directories
which exist, so its job names change with every blueprint added. A required check which nobody
produces any more blocks every pull request. `verified` waits for all of them and keeps its
name.

## Writing style

The rule for how we write is in [`AGENTS.md`](./AGENTS.md), under `How we write`, in the form an
agent reads. It holds for every English text here: the READMEs and `AGENTS.md` files of the
blueprints, this file, the Javadoc and the comments which explain something, in the main code
and in the tests, log messages, and the texts of commits and pull requests. In short:

Most people who read a blueprint read English as a second language, and so does the
maintainer. Long sentences, rare words and stacked nouns slow them down. Write so that
nobody has to read a sentence twice.

Short main sentences, one thought each. One subordinate clause is enough. Active voice.
The common word instead of the rare one: `use` instead of `leverage`, `about` instead of
`regarding`, `so` instead of `consequently`, `has` instead of `possesses`. A technical term
stays a technical term, but say what it means the first time it turns up, and write an
abbreviation out once. If a sentence trips you up when you read it aloud, rewrite it.

People read blueprints more often than they run them, so the prose matters as much as the
code. Write plainly: no em dashes, no bullet lists whose items start with a bolded label, no
groups of three for the sake of rhythm, no phrases about how important something is. Say what
a thing is, not what it "serves as". Drop hedging and filler.

Nothing a program reads is renamed for the sake of language. Class and method names,
configuration keys and artifact coordinates stay as they are, because the tests and the
other repositories point at them.

### What a blueprint may point at

A blueprint explains how the framework behaves by linking the
[wiki](https://github.com/vanillabp/adapter-platform-integration/wiki). The wiki is the
documentation of that behaviour for users, and it is corrected when the behaviour changes.
Nothing here cites a framework story number, an issue or a pull request. Those record a
conversation at a point in time, and the reader of a blueprint cannot reach them. So a citation
into one is a dead end, and nothing in any build notices it.

Where the wiki does not say it, the blueprint says it in its own words, completely enough to
stand on its own. The missing sentence becomes a change to the wiki. A blueprint may run into
something the framework does not cover at all. That is a finding for the framework. It goes
into the framework's roadmap as a story, not into a register here. This repository had such a
register until every one of its entries was closed. Keeping it after that only invited
citations into it.

The framework's own repositories keep a `DECISIONS.md` next to their code. A blueprint does not
cite it either. It belongs to that repository and speaks to whoever changes it. A blueprint
speaks to somebody who writes an application.

## Formatting

Spotless runs during `process-sources` and fails the build on violations:

```bash
./mvnw -N spotless:apply
```

The goal runs without recursion (`-N`), because Spotless is declared in the root POM only. A
blueprint's POM shows what a VanillaBP application needs, and none of our build tooling.

The Eclipse JDT formatter configured in `formatting_conventions.xml` formats Java (2-space
indentation, import order `java,javax,org,com,at.phactum`, one line per method call in fluent
APIs of more than one call). POMs are sorted, and Markdown is formatted as well.

Two kinds of YAML are left out, both because the formatter drops comments. The first are GitHub
workflow files, where it also removes the quotes around version numbers and turns `"3.10"` into
`3.1`. The second is everything below a blueprint's `src/`, where the comments are what the
blueprint teaches.

## License

By contributing you agree that your contribution is licensed under the
[Apache License, Version 2.0](LICENSE).
