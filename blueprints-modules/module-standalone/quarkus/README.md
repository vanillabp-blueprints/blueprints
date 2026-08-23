![Header](./readme/vanillabp-headline.png)

# The application is the workflow module

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

One Maven module, one artifact, one use case: the application and the workflow module are
the same thing. That is allowed, it costs one file, and it is the right shape whenever it is
clear that there will never be a second use case in this deployment. A delta on top of
`module-single`, and the smallest project VanillaBP runs in.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

A loan approval process consisting of one service task. Starting it stores a *workflow
aggregate* and starts a workflow in the BPMS, the service task fills the aggregate, and the
process ends.

What is worth looking at:

- **One file makes it a workflow module.** `src/main/resources/META-INF/workflow-module`
  contains `loan-approval`, and that is the whole declaration. There is no way around naming
  the module: the ID is what the BPMS sees, what the module's configuration file is named
  after, and what keeps the identifiers of this application apart from the next one on the
  same engine.
- **The BPMN files sit at the classpath root**, in `processes/<adapter-id>/`. In a project
  whose workflow module is a JAR of its own they live below a directory named after the
  module, because several modules share one classpath and would collide. Here nothing can
  collide, so VanillaBP looks in both places and this one is the shorter.
- **The module's own configuration file sits next to `application.yaml`**, as
  `loan-approval.yaml`. Keeping it separate is still worth it: it says what a second
  application reusing this use case would have to bring along, and what belongs to this
  deployment.

  If you are certain that this use case will never be split into a Maven module of its own,
  drop the file and move its `loan-approval:` block into `application.yaml`. Nothing else
  changes: the properties keep their names, `LoanApprovalProperties` keeps reading them, and
  the only thing given up is the line between what the module needs and what the deployment
  provides.

- **Nothing else changes.** Same packages, same four classes per direction of the BPMN
  wiring, same aggregate, same tests. There is not even an index to build here: the classes
  belong to the artifact the build processes anyway, which is what a workflow module in a
  Maven module of its own needs one for. Splitting the use case out later is a move of files
  plus a second POM, not a rewrite, and `module-multi` is what it moves to.

One class per direction of the BPMN wiring, as everywhere else. `Service` is the business
code and never touches VanillaBP. `Workflow` is what the application tells the process and
the only place `ProcessService` is injected. `WorkflowTaskHandler` is what the process tells
the application: it carries `@WorkflowService` and every `@WorkflowTask` method and calls
`Service`. That shape does not get simpler because the artifact got smaller, and it is what
keeps the two beans from depending on each other.

There is no `vanillabp.*` property anywhere: with one adapter on the classpath and one
workflow module, VanillaBP derives the adapter, the module and the location of the BPMN files
by convention. Zero configuration is not a promise about small projects, it is what the
conventions are for.

**When this shape is wrong:** the moment a second use case shows up. Two use cases in one
artifact share a classpath with nothing keeping them apart, and the collisions are the ones
[`module-multi`](https://github.com/vanillabp-blueprints/module-multi-quarkus) spells out:
bean names, entity names, HTTP paths, and the identifiers the BPMS sees. Nothing in this
blueprint prevents that split later, and nothing here pays for it in advance.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|                        File                        |                                        What is different                                        |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `pom.xml`                                          | one POM instead of three, and no index plugin: the application's own classes are indexed anyway |
| `src/main/resources/META-INF/workflow-module`      | the same file, now in the application's own resources                                           |
| `src/main/resources/processes/<adapter-id>/*.bpmn` | the BPMN files at the classpath root instead of below a directory named after the module        |
| `src/main/resources/loan-approval.yaml`            | the module's configuration next to `application.yaml` instead of inside a module JAR            |
| `src/test/.../ApplicationSmokeTest.java`           | the same test, in the same artifact as the module's own integration test                        |

Everything else is the base blueprint, file for file: the process, the aggregate, the four
wiring classes, the API and what the tests assert.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run and be pointed at. Start one; its
address lives in `src/main/resources/application-camunda8.yaml`, and there is only one of
those files because there is only one artifact:

```yaml
vanillabp:
  adapters:
    camunda8:
      rest-address: http://localhost:8080
      # Nothing else is needed: this adapter keeps workflow modules apart by nothing at all
      # ('name-clash-avoidance: none') unless told otherwise, because a cluster started from
      # the stock image has multi-tenancy switched off and rejects a tenant per module. The
      # adapter warns about it while booting - with one workflow module the identifiers are
      # unique anyway. Set 'name-clash-avoidance: use-prefix' to have VanillaBP prefix them.
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
mvn quarkus:dev
```

Nothing about identifiers shows up at startup: the BPMS profiles of this blueprint set
`name-clash-avoidance: use-prefix`, so VanillaBP puts the workflow module ID in front of every
identifier before it reaches the engine and takes it off again on the way back. The BPMN files,
the business code and the rest of the configuration keep the plain names, and no tenant is
involved, which matters on a BPMS licensed per tenant. What the modes are and what each of them
costs is in
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided).

Start a loan approval. This is the only URL you need:

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

The Camunda 8 profile loads its own file and nothing of the other engine's: a profile file
applies to its own engine and to no other. Naming an adapter id whose adapter is not on the
classpath is a configuration error VanillaBP refuses to start with, and the profiles are what
keeps that from happening.

## How it works

|                            File                            |                                                 Role                                                  |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `src/main/resources/META-INF/workflow-module`              | contains `loan-approval` and thereby declares this application to be a workflow module                |
| `src/main/resources/processes/camunda7/loan_approval.bpmn` | the process: start event, service task, end event. The task names the method implementing it          |
| `.../loanapproval/model/Aggregate.java`                    | the workflow aggregate, a normal JPA entity keyed by the loan request ID                              |
| `.../loanapproval/Service.java`                            | the business code: builds the aggregate and tells `Workflow` that a loan was requested                |
| `.../loanapproval/Workflow.java`                           | what the application tells the process; the only class using `ProcessService`                         |
| `.../loanapproval/WorkflowTaskHandler.java`                | what the process tells the application: `@WorkflowService`, `@WorkflowTask`, calls `Service`          |
| `.../loanapproval/ApiController.java`                      | the GET endpoints operating the process                                                               |
| `.../loanapproval/config/LoanApprovalProperties.java`      | the module's own configuration, read from `loan-approval.yaml`                                        |
| `src/test/.../LoanApprovalIT.java`                         | starts a real workflow and waits for the aggregate to have been filled                                |
| `src/test/.../WorkflowModuleTest.java`                     | the base class it inherits from: waiting for workflow progress, identical in every blueprint          |
| `src/test/.../ApplicationSmokeTest.java`                   | boots the application and checks that the workflow module declaring itself is the one VanillaBP wired |

The order of events: `ApiController` calls `Service#initiateLoanApproval`, which builds the
aggregate and tells `Workflow` what happened, namely `loanRequested`, not "start the
process". `Workflow#loanRequested` calls `ProcessService#startWorkflow`, and VanillaBP
persists the aggregate and starts the process in the same transaction, so an aggregate
without a workflow, or the other way round, cannot happen. The BPMS then reaches the service
task and calls `WorkflowTaskHandler#retrieveCreditRating`, which does nothing but hand over
to `Service#assessCreditRating`, with the aggregate loaded before and saved after the call.
That happens in a transaction VanillaBP owns, which is why neither of the two classes
declares one of its own. Only the method the API calls does, since starting a workflow has
to run in a transaction. Putting `@Transactional` on a task handler anyway fails the boot
with a message naming the method, and putting it on a bean the handler calls fails the task
while it runs, so this is a rule VanillaBP enforces rather than one to remember.

That the test waits instead of asserting immediately is not accidental: a BPMS runs tasks in
its own transactions, and a remote one does so eventually. A test assuming otherwise passes
on one engine and fails on the next.

## Documentation

- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
- [Defining a workflow module](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#defining-a-workflow-module): the marker file, resource conventions and the module's own configuration files
- [Provide an index for workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#provide-index-for-workflow-modules): why a module in a Maven module of its own needs one, and why this blueprint does not
- [How name clashes are avoided](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided): what the warning at startup is about, and the modes keeping two workflow modules apart
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
