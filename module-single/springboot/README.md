# Application plus one workflow module

This is the base blueprint — the normal case, and the one to start from. A workflow module
is a JAR containing BPMN models and the code implementing them; an application pulls it in
as a dependency and decides which business process engine is used. If you build a business
process application with VanillaBP, this is the shape it has.

## What this blueprint shows

A loan approval process consisting of one service task. Starting it stores a *workflow
aggregate* and starts a workflow in the BPMS, the service task fills the aggregate, and the
process ends.

Four things are worth looking at:

- **The workflow module is a JAR of its own** (`loan-approval/`) and cannot be started
  alone. It declares itself by the marker file `META-INF/workflow-module` containing its ID.
- **It knows no BPMS.** Its only VanillaBP dependency is `vanillabp-spring-boot-support`,
  which deliberately exposes no engine API. The adapter is a dependency of the application
  (`application/`) — the BPMN files are the only thing that differs between engines, which
  is why they live in `processes/<adapter-id>/`.
- **It brings its own configuration.** `loan-approval/loan-approval.yaml` inside the module
  is loaded automatically and takes precedence over `application.yaml`. Configuration a
  module needs stays with the module instead of scattering across the project.
- **It is tested on its own.** The integration test lives in the workflow module and brings
  a minimal application with it; the application only carries a smoke test.

There is no `vanillabp.*` property anywhere: with one adapter on the classpath and one
workflow module, VanillaBP derives the adapter, the module and the location of the BPMN
files by convention.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile — not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run and be pointed at. Start one, then add
its address to `application/src/main/resources/application.yaml` and to
`loan-approval/src/test/resources/application.yaml`:

```yaml
vanillabp:
  adapters:
    camunda8:
      rest-address: http://localhost:8080
```

Without it the application does not boot, and says so:

```
Camunda 8 adapter 'camunda8' is used but not configured: the property
'vanillabp.adapters.camunda8.rest-address' is missing.
```

That is the normal way to work with VanillaBP: configuration is validated while booting, and
the message names what to do.

Start the application:

```bash
mvn -pl application spring-boot:run
```

Start a loan approval — this is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

It answers with the ID of the loan request and logs the URL showing the result:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

Opening that URL shows the aggregate, including the credit rating the service task wrote.

## How it works

|                                          File                                          |                                                              Role                                                               |
|----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                            | contains `loan-approval` and thereby declares this JAR to be a workflow module                                                  |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: start event, service task, end event. The task names the method implementing it                                    |
| `.../loanapproval/model/Aggregate.java`                                                | the workflow aggregate, a normal JPA entity keyed by the loan request ID                                                        |
| `.../loanapproval/Service.java`                                                        | `@WorkflowService` bound to the BPMN process, `@WorkflowTask` implementing the service task, and the method starting a workflow |
| `.../loanapproval/ApiController.java`                                                  | the GET endpoints operating the process                                                                                         |
| `.../loanapproval/config/LoanApprovalProperties.java`                                  | the module's own configuration                                                                                                  |
| `application/.../Application.java`                                                     | the Spring Boot application; its package is the parent of the module's, so scanning finds everything                            |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | starts a real workflow and waits for the aggregate to have been filled                                                          |
| `application/src/test/.../ApplicationSmokeTest.java`                                   | boots the application, which is where VanillaBP validates that every BPMN task is wired to code                                 |

The order of events when a workflow starts: `Service#initiateLoanApproval` builds the
aggregate and calls `ProcessService#startWorkflow`. VanillaBP persists the aggregate and
starts the process in the same transaction, so an aggregate without a workflow — or the
other way round — cannot happen. The BPMS then reaches the service task and calls
`Service#retrieveCreditRating`, with the aggregate loaded before and saved after the call.

That the test waits instead of asserting immediately is not accidental: a BPMS runs tasks in
its own transactions, and a remote one does so eventually. A test assuming otherwise passes
on one engine and fails on the next.

## Documentation

- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules) — what a workflow module is, its ID, and where its BPMN files are looked for
- [Defining a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#defining-a-workflow-module) — the marker file, resource conventions and the module's own configuration files
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates) — why there are no process variables
- [Wire up a process / Wire up a task](https://github.com/vanillabp/spi-for-java#usage) — the annotations used in `Service.java`
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use — how a BPMN task has to be modelled for that engine

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints) — this repository is a
read-only mirror, **issues and pull requests belong there.**
