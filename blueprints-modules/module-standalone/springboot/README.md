![Header](./readme/vanillabp-headline.png)

# The application is the workflow module

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

One Maven module, one artifact, one use case: the application and the workflow module are the
same thing. That is allowed, and it costs one file. It is the right shape whenever it is clear
that this deployment will never have a second use case. A delta on top of `module-single`, and
the smallest project VanillaBP runs in.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

A loan approval process with one service task. Starting it stores a *workflow aggregate* and
starts a workflow in the BPMS. The service task fills the aggregate, and the process ends.

The aggregate stays in the application. It carries `@NoSyncWithBPMS`, and no element of this
model reads an attribute. So the BPMS (business process management system) holds nothing but
the aggregate's ID, which VanillaBP needs to find the workflow again. An attribute which a
condition, a timer or a multi-instance collection reads is annotated `@SyncWithBPMS` and is
shared. No other attribute is.

What is worth looking at:

- **One file makes it a workflow module.** `src/main/resources/META-INF/workflow-module`
  contains `loan-approval`, and that is the whole declaration. You cannot avoid naming the
  module. The BPMS sees the ID, the module's configuration file is named after it, and it keeps
  the identifiers of this application apart from the next one on the same engine.
- **The BPMN files are at the classpath root**, in `processes/<adapter-id>/`. In a project
  whose workflow module is a JAR of its own, they are below a directory named after the module.
  There several modules share one classpath and would collide. Here nothing can collide.
  VanillaBP looks in both places, and this one is shorter.
- **The module's own configuration file is next to `application.yaml`**, as
  `loan-approval.yaml`. Keeping it separate is still worth it. It says what a second
  application which reuses this use case would have to bring along, and what belongs to this
  deployment.

  If you are sure that this use case will never be split into a Maven module of its own, drop
  the file and move its `loan-approval:` block into `application.yaml`. Nothing else changes.
  The properties keep their names, and `LoanApprovalProperties` keeps reading them. You only
  give up the line between what the module needs and what the deployment provides.

- **Nothing else changes.** Same packages, same four classes per direction of the BPMN wiring,
  same aggregate, same tests. Splitting the use case into a JAR of its own later means moving
  files and adding a second POM, not a rewrite. It then becomes `module-multi`.

One class per direction of the BPMN wiring, as everywhere else. `Service` is the business code
and never touches VanillaBP. `Workflow` is what the application tells the process and the only
place `ProcessService` is injected. `WorkflowTaskHandler` is what the process tells the
application. It carries `@WorkflowService` and every `@WorkflowTask` method, and it calls
`Service`. That shape does not get simpler because the artifact got smaller. And it keeps the
two beans from depending on each other.

There is no `vanillabp.*` property anywhere. With one adapter on the classpath and one workflow
module, VanillaBP finds the adapter, the module and the location of the BPMN files by
convention. Zero configuration is not a promise for small projects only. It is what the
conventions are for.

**When this shape is wrong:** as soon as a second use case shows up. Two use cases in one
artifact share a classpath, and nothing keeps them apart. The collisions are the ones
[`module-multi`](https://github.com/vanillabp-blueprints/module-multi-springboot) lists: bean
names, entity names, HTTP paths, and the identifiers the BPMS sees. Nothing in this blueprint
prevents that split later, and nothing here pays for it in advance.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|                        File                        |                                      What is different                                       |
|----------------------------------------------------|----------------------------------------------------------------------------------------------|
| `pom.xml`                                          | one POM instead of three: no aggregator, no module, the application carries the dependencies |
| `src/main/resources/META-INF/workflow-module`      | the same file, now in the application's own resources                                        |
| `src/main/resources/processes/<adapter-id>/*.bpmn` | the BPMN files at the classpath root instead of below a directory named after the module     |
| `src/main/resources/loan-approval.yaml`            | the module's configuration next to `application.yaml` instead of inside a module JAR         |
| `src/test/.../ApplicationSmokeTest.java`           | the same test, in the same artifact as the module's own integration test                     |
| `TestApplication.java`                             | gone: the application is right here, so a test does not need a stand-in for it               |

Everything else is the base blueprint, file for file: the process, the aggregate, the four
wiring classes, the API and what the tests assert.

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
`src/main/resources/application-camunda8.yaml`. There is only one such file, because there is
only one artifact:

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
mvn spring-boot:run
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

|                            File                            |                                                 Role                                                  |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `src/main/resources/META-INF/workflow-module`              | contains `loan-approval`, which makes this application a workflow module                              |
| `src/main/resources/processes/camunda7/loan_approval.bpmn` | the process: start event, service task, end event. The task names the method implementing it          |
| `.../loanapproval/model/Aggregate.java`                    | the workflow aggregate, a normal JPA entity keyed by the loan request ID and shared with no BPMS      |
| `.../loanapproval/Service.java`                            | the business code: builds the aggregate and tells `Workflow` that somebody requested a loan           |
| `.../loanapproval/Workflow.java`                           | what the application tells the process; the only class using `ProcessService`                         |
| `.../loanapproval/WorkflowTaskHandler.java`                | what the process tells the application: `@WorkflowService`, `@WorkflowTask`, calls `Service`          |
| `.../loanapproval/ApiController.java`                      | the GET endpoints operating the process                                                               |
| `.../loanapproval/config/LoanApprovalProperties.java`      | the module's own configuration, read from `loan-approval.yaml`                                        |
| `.../workflowmodule/Application.java`                      | the Spring Boot application, which is also the workflow module                                        |
| `src/test/.../LoanApprovalIT.java`                         | starts a real workflow and waits for the aggregate to have been filled                                |
| `src/test/.../WorkflowModuleTest.java`                     | its base class: waits for the workflow to make progress. The same in every blueprint                  |
| `src/test/.../ApplicationSmokeTest.java`                   | boots the application and checks that the workflow module declaring itself is the one VanillaBP wired |

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
- [Sharing workflow-aggregate data](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#fine-grained-control-over-attributes-synchronized-to-the-bpms): `@NoSyncWithBPMS`, `@SyncWithBPMS`, and what a BPMS gets to see
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
