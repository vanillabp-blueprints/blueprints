# Contributing

Thanks for improving the VanillaBP blueprints. This page describes the rules a blueprint has
to follow. The rationale behind them is always the same: a blueprint is read more often than
it is run, and it is read by developers *and* by AI agents.

## Prerequisites

- JDK 21
- Git; Maven is provided by the wrapper (`./mvnw`)

## Where changes go

Issues and pull requests are handled in this repository. The per-blueprint repositories
below `vanillabp-blueprints` are mirrors written by CI — changes pushed there are lost.

## Rule 1: every blueprint builds on its own

A blueprint is split into a repository of its own and cloned from there. Its POM therefore
must not depend on anything that only exists in this monorepo:

- **Do not** use the root POM as a Maven parent. Blueprints use the parent their platform
  needs (Spring Boot's `spring-boot-starter-parent`, Quarkus' BOM import).
- **Do not** rely on properties, dependency management or plugin management declared here.
- Declare versions, the VanillaBP BOM import and the BPMS profiles in the blueprint's own
  POM.

The price is duplication between blueprints, and it is paid on purpose: a reader must be
able to understand a blueprint from the files in front of them. CI keeps the copies from
drifting apart.

The root POM aggregates the blueprints (`<modules>`) and checks formatting for the whole
repository — nothing else.

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
would make the class depend on `Service` while `Service` depends on it — a circular bean
reference Spring Boot rejects at startup unless worked around with `@Lazy`; an interface
does not help, because the cycle is between beans, not types.

All four classes exist even where a blueprint only forwards through them, as in
`module-single`. Blueprints which need glue code — message correlation, asynchronous tasks —
then only add methods instead of restructuring, which is what makes several deltas
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
2. At every wait state the handler logs the URLs to continue with — one fully populated,
   clickable URL per possible continuation.
3. No `curl`, no request body, no tool beyond the browser.

The API therefore consists of GET requests only. Besides being convenient for humans, the
logged URLs describe the reached process state in a way an agent can read.

## Rule 4: no BPMS specifics

Blueprints only cover what `spi-for-java` and `adapter-platform-integration` provide.
Anything specific to a BPMS belongs into that adapter's wiki. This is possible because
switching the BPMS is a Maven profile, not a code change.

Reference documentation is linked, never copied.

## Rule 5: the aspect is proven by a test

A blueprint ships an integration test which plays through the aspect it shows, using a
simulator for surrounding systems, plus a smoke test of the application. That test is the
verification loop for generated code — without it, a blueprint cannot be used by an agent.

The workflow module is tested by running it: since a workflow module is a JAR which cannot
be started alone, its test sources bring a minimal application (`TestApplication`) along.
Assert on the **workflow aggregate**, never on the engine — the aggregate is the only state
that means the same on every BPMS — and **wait** instead of asserting immediately, because a
remote BPMS gets to a task eventually.

## The test harness

What every blueprint needs for that is the same, so it is written once and copied:

```
templates/test-harness/springboot/
├── workflow-module/         -> <maven-module>/src/test/java/blueprint/workflowmodule/
│   ├── TestApplication.java     boots the workflow module for its test
│   ├── WorkflowModuleTest.java  base class: waiting for a workflow to make progress
│   └── Simulator.java           base class of a stand-in for a surrounding system
└── application/             -> <maven-module>/src/test/java/blueprint/workflowmodule/
    └── ApplicationSmokeTest.java
```

Copies rather than a shared module, because a blueprint has to build standalone after the
split and an agent has to be able to read the code that verifies its work.

```bash
python3 bin/sync_harness.py            # copy the reference over every existing copy
python3 bin/sync_harness.py --check    # what CI does
```

**Never edit a copy** — change the reference and run the sync. The script only refreshes
copies which already exist; putting the first one into a new blueprint is part of creating
it, and a blueprint takes only the files it needs (`Simulator.java` only if it has a
surrounding system to simulate).

Everything else in a blueprint's tests is specific to it and is never compared: the
integration test *is* the proof of the aspect and is supposed to differ.

## Adding a blueprint

1. Create `<blueprint-id>/<platform>/` and add it to `<modules>` of the root POM.
2. Copy the structure of `module-single/<platform>/` and apply your delta, including the
   test harness files it needs (see below).
3. Write `README.md` (for humans) and `AGENTS.md` (for agents) from the templates.
4. Add the entry to `blueprints.yaml` of the `.github` repository. CI flips
   `platforms.<platform>.status` to `available` once the blueprint has been split out.
5. `./mvnw install verify` for every BPMS profile the blueprint supports. Only `camunda7`
   runs without infrastructure — it is embedded. `camunda8` needs a running cluster and its
   `rest-address` configured, which is why it is not part of the default build.

The index and this repository have to agree — every blueprint directory is a module of the
root POM and has an index entry, and no index entry claims a blueprint which is not here.
CI checks that on every push; to check it locally:

```bash
curl -fsSLO https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/blueprints.yaml
python3 bin/check_index_consistency.py blueprints.yaml
```

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

## Formatting

Spotless runs during `process-sources` and fails the build on violations:

```bash
./mvnw -N spotless:apply
```

The goal is invoked non-recursively (`-N`) because Spotless is declared in the root POM
only — a blueprint's POM shows what a VanillaBP application needs and no build tooling of
ours.

Java is formatted by the Eclipse JDT formatter configured in `formatting_conventions.xml`
(2-space indentation, import order `java,javax,org,com,at.phactum`, one line per method call
in fluent APIs of more than one call). POMs are sorted, Markdown is formatted as well.

Two kinds of YAML are excluded, both because the formatter drops comments: GitHub workflow
files (where it also unquotes version numbers, turning `"3.10"` into `3.1`) and everything
below a blueprint's `src/` — in a blueprint the comments are what is being taught.

## License

By contributing you agree that your contribution is licensed under the
[Apache License, Version 2.0](LICENSE).
