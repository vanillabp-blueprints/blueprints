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
├── ApiController.java
├── Service.java                     <- @WorkflowService
├── config/<UseCase>Properties.java
└── model/
    ├── Aggregate.java
    └── AggregateRepository.java
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

## Adding a blueprint

1. Create `<blueprint-id>/<platform>/` and add it to `<modules>` of the root POM.
2. Copy the structure of `module-single/<platform>/` and apply your delta.
3. Write `README.md` (for humans) and `AGENTS.md` (for agents) from the templates.
4. Add the entry to `blueprints.yaml` of the `.github` repository. CI flips
   `platforms.<platform>.status` to `available` once the blueprint has been split out.
5. `./mvnw install verify` for every BPMS profile the blueprint supports.

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
./mvnw spotless:apply
```

Java is formatted by the Eclipse JDT formatter configured in `formatting_conventions.xml`
(2-space indentation, import order `java,javax,org,com,at.phactum`, one line per method call
in fluent APIs of more than one call). POMs are sorted, Markdown and YAML are formatted as
well.

## License

By contributing you agree that your contribution is licensed under the
[Apache License, Version 2.0](LICENSE).
