![Header](./readme/vanillabp-headline.png)

# Application plus one workflow module

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

This is the base blueprint. It shows the normal case, and it is the one to start from. A
workflow module is a JAR with BPMN (Business Process Model and Notation) models and the code
which implements them. An application pulls it in as a dependency and decides which business
process engine runs it. If you build a business process application with VanillaBP, this is the
shape it has.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

A loan approval process with one service task. Starting it stores a *workflow aggregate* and
starts a workflow in the BPMS (business process management system). The service task fills the
aggregate, and the process ends.

What is worth looking at:

- The workflow module is a JAR of its own (`loan-approval/`) and cannot be started alone. It
  declares itself by the marker file `META-INF/workflow-module` containing its ID.
- Everything it owns is named after that ID. Workflow modules are not isolated by classloaders.
  They share one classpath. So each module needs a namespace of its own in two places: a unique
  Java package (`blueprint.workflowmodule.loanapproval`) and a single resource subdirectory
  (`src/main/resources/loan-approval/`) which holds *all* of its resources. The marker file is
  the one exception. It has to be in `META-INF/`.
- It knows no BPMS. Its only VanillaBP dependency is `vanillabp-spring-boot-support`, which
  exposes no engine API on purpose. The adapter is a dependency of the application
  (`application/`). BPMN files are the only thing that differs between engines. That is why
  they are in `processes/<adapter-id>/`.
- It brings its own configuration. `loan-approval/loan-approval.yaml` inside the module is
  loaded automatically and wins over `application.yaml`. So the configuration a module needs
  stays with the module and is not spread across the project.
- One class per direction of the BPMN wiring. `Service` is the business code and never touches
  VanillaBP. `Workflow` is what the application tells the process and the only place
  `ProcessService` is injected. `WorkflowTaskHandler` is what the process tells the
  application. It carries `@WorkflowService` and every `@WorkflowTask` method, and it calls
  `Service`. Here each of them forwards a single line, and that is exactly why it is worth
  seeing. The shape stays the same once a process needs to correlate messages or complete
  tasks. And it keeps the two beans from depending on each other.
- The aggregate decides what the BPMS gets to see. It carries `@NoSyncWithBPMS`, and no
  attribute takes that back, because no expression in this model reads the aggregate. Only the
  loan request id is passed on, because VanillaBP needs it to find the workflow again. An
  attribute which a condition or a timer reads would carry `@SyncWithBPMS`. No other attribute
  would.
- It is tested on its own. The integration test is in the workflow module and brings a minimal
  application with it. The application only has a smoke test.

There is no `vanillabp.*` property anywhere. With one adapter on the classpath and one workflow
module, VanillaBP finds the adapter, the module and the location of the BPMN files by
convention.

## Running it

Requires a JDK 21 or newer. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

To run it on another BPMS, pick another Maven profile. Not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run. Start one. Its address and everything
else specific to that engine are in its profile file
`application/src/main/resources/application-camunda8.yaml`. The module's own test has a copy:

```yaml
vanillabp:
  adapters:
    camunda8:
      # Camunda 8 is a remote engine: point this at your cluster.
      rest-address: http://localhost:8080
      # This adapter leases the jobs it holds, so the cluster takes the answer of the current
      # activation only. The key has no default, because a lease cannot be taken back. This is
      # the value the adapter recommends:
      # https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/wiki/Configuration#a-job-somebody-else-holds
      job-lease: use
```

The file is loaded because the Maven profile `camunda8` sets the Spring profile of the same
name. So you choose the engine once, on the Maven command line. The build, the tests and
`spring-boot:run` all follow it.

Without it the application does not boot. This is the normal way to work with VanillaBP: it
checks the configuration while it boots, and the message names the property to set.

Start the application:

```bash
mvn -pl application spring-boot:run
```

You see nothing about identifiers at startup. The BPMS profiles of this blueprint set
`name-clash-avoidance: use-prefix`. So VanillaBP puts the workflow module ID in front of every
identifier before it reaches the engine, and takes it off again on the way back. The BPMN
files, the business code and the rest of the configuration keep the plain names. No tenant is
involved, which matters on a BPMS licensed per tenant.
[The wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided)
explains the modes and what each of them costs.

Start a loan approval. This is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

It answers with the ID of the loan request and logs the URL which shows the result:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

Opening that URL shows the aggregate, including the credit rating the service task wrote.

Camunda 7 serves its own web applications, and the `camunda7` profile of this blueprint
configures a user for them. The
[adapter's wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Cockpit-Tasklist-and-Admin)
says where they are served and how to log in.

## How it works

|                                          File                                          |                                                   Role                                                    |
|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                            | contains `loan-approval`, which makes this JAR a workflow module                                          |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: start event, service task, end event. The task names the method implementing it              |
| `.../loanapproval/model/Aggregate.java`                                                | the workflow aggregate, a normal JPA entity keyed by the loan request ID                                  |
| `.../loanapproval/Service.java`                                                        | the business code: builds the aggregate and tells `Workflow` that somebody requested a loan               |
| `.../loanapproval/Workflow.java`                                                       | what the application tells the process; the only class using `ProcessService`                             |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | what the process tells the application: `@WorkflowService`, `@WorkflowTask`, calls `Service`              |
| `.../loanapproval/ApiController.java`                                                  | the GET endpoints operating the process                                                                   |
| `.../loanapproval/config/LoanApprovalProperties.java`                                  | the module's own configuration                                                                            |
| `application/.../Application.java`                                                     | the Spring Boot application; its package is the parent of the module's, so scanning finds everything      |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | starts a real workflow and waits for the aggregate to have been filled                                    |
| `loan-approval/src/test/.../WorkflowModuleTest.java`                                   | its base class: boots the module and waits for the workflow to make progress. The same in every blueprint |
| `application/src/test/.../ApplicationSmokeTest.java`                                   | boots the application, which is where VanillaBP validates that every BPMN task is wired to code           |

The order of events: `ApiController` calls `Service#request`. That method builds the aggregate
and tells `Workflow` what happened, namely `loanRequested`, not "start the process".
`Workflow#loanRequested` calls `ProcessService#startWorkflow`. VanillaBP stores the aggregate
and starts the process in the same transaction, so you never get an aggregate without a
workflow, or the other way round. The BPMS then reaches the service task and calls
`WorkflowTaskHandler#retrieveCreditRating`. That method only hands over to
`Service#assessCreditRating`. VanillaBP loads the aggregate before the call and saves it after.
This happens in a transaction VanillaBP owns, so neither of the two classes declares one of its
own. Only the method the API calls does, because starting a workflow has to run in a
transaction. If you put `@Transactional` on a task handler anyway, the boot fails with a
message naming the method. If you put it on a bean the handler calls, the task fails while it
runs. So VanillaBP enforces this rule, and you do not have to remember it.

The test waits instead of asserting at once, and that is on purpose. A BPMS runs tasks in its
own transactions, and a remote one runs them some time later. A test which assumes otherwise
passes on one engine and fails on the next.

## Documentation

- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
- [Defining a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#defining-a-workflow-module): the marker file, resource conventions and the module's own configuration files
- [How name clashes are avoided](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided): the modes keeping two workflow modules apart, and what each of them costs
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables
- [Wire up a process / Wire up a task](https://github.com/vanillabp/spi-for-java#usage): the annotations used in `WorkflowTaskHandler.java`
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
