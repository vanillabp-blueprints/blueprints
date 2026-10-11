![Header](./readme/vanillabp-headline.png)

# Sections of a process and their data

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

A process which no longer fits on a screen is cut into sections. Each section usually gets an
object of its own on the workflow aggregate. That object does not exist before the section
runs. If the process skips the section, the object never exists. This blueprint shows how a
model asks about such a section. It also shows what happens when the model reaches into the
object instead.

## What this blueprint shows

![The loan approval, with one section as a call activity and one as an embedded subprocess](docs/loan_approval.png)

![The called process: the document check](docs/document_check.png)

A loan approval in two sections. The document check is a call activity, so it has a model of
its own. The risk assessment is an embedded subprocess, and it runs for a big loan only. Both
shapes are normal ways to hold a section. Both are used here, so that the pattern is not tied
to one of them.

Each section keeps its findings in an object of its own:

```java
@Embedded
private DocumentCheck documentCheck;

@Embedded
private RiskAssessment riskAssessment;
```

The first task of a section creates that object, and the later tasks fill it in. A small loan
never enters the risk assessment. So it reaches its decision with `riskAssessment` still empty,
and it stays empty for the rest of that workflow.

### The good case

The process asks two questions after the sections, and the aggregate answers both with a
plain `boolean`:

```java
@SyncWithBPMS
public boolean isRiskAssessed() {
  return riskAssessment != null;
}

@SyncWithBPMS
public boolean isCollateralSufficient() {
  return (riskAssessment != null)
      && Boolean.TRUE.equals(riskAssessment.getCollateralSufficient());
}
```

`isRiskAssessed` is the one to copy. Whether a section ran is a fact about the workflow, and
the model may ask about it. A getter on the top level of the aggregate is the place to answer
it. The gateway after the two paths reads `${riskAssessed}` and nothing else.

The type is part of the answer. A getter the model reads returns `boolean`, not `Boolean`. A
gateway has to take a path, and every engine treats an empty value in a condition its own way.

Inside a section it is different. There the section's object exists, because the first task of
the section made it. So the model of the section may read the section's own findings. The
document check asks `${signaturesComplete}`, and the risk assessment asks
`${collateralSufficient}`, each inside itself. The getters still check for null. One aggregate
serves every model of the workflow module. And a getter which relies on where it is called from
will be moved by somebody one day.

Such a value also stays where the section is. A called process is a process instance of its own
and keeps its own variables. So what the document check shares is gone once it ends. An
embedded subprocess shares the variables of the process instance around it. That is why the
risk assessment can answer the gateway which follows it.

### The bad case

The same gateway, written the way that seems obvious:

```xml
<bpmn:conditionExpression>${riskAssessment.collateralSufficient}</bpmn:conditionExpression>
```

For a big loan this works. For a small one the path does not resolve. `riskAssessment` is
empty, so there is nothing to read `collateralSufficient` from. VanillaBP stops the workflow
with an error which names the path and the part of it which is empty. It does not hand the
model a value nobody computed. Writing `false` instead would be worse. Nobody could tell a
`false` which nobody computed from one the application worked out.

Two ways lead out, and both are a decision somebody writes down:

1. Put the question on the top level of the aggregate, as `isRiskAssessed` above. The aggregate
   knows what an absent section means, so the model does not have to.
2. Declare a substitute for exactly that path in the configuration of the workflow. Then the
   value is stated, not guessed.

The same mistake exists in Java, one line further in:

```java
public boolean isCollateralSufficient() {
  return riskAssessment.getCollateralSufficient();   // fails for a small loan
}
```

`LoanApprovalIT` has a test for it. It builds an aggregate which has reached no section at all,
and asks every getter the model reads. That is the cheapest way to find out that one of them
forgot its null check.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|            File            |                                         What is different                                          |
|----------------------------|----------------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`       | a call activity, an embedded subprocess, and the gateway which asks whether the second section ran |
| `document_check.bpmn`      | new: the called process of the first section                                                       |
| `Aggregate.java`           | one sub-object per section, and the four boolean getters the models read                           |
| `DocumentCheck.java`       | new: the findings of the first section                                                             |
| `RiskAssessment.java`      | new: the findings of the second section, absent for a small loan                                   |
| `WorkflowTaskHandler.java` | the tasks of both sections, and `secondaryBpmnProcesses` for the called process                    |
| `Service.java`             | the business code of both sections: the first task creates the object, the later ones fill it in   |
| `loan-approval.yaml`       | the limit which makes the second section run, and the numbers the two sections use                 |
| `LoanApprovalIT.java`      | runs a loan through each path and checks that the getters answer without the skipped section       |

Both BPMN files are in the same directory of the workflow module and are deployed together. A
called process belongs to the module which owns it. It is not a module of its own.

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

Start the application:

```bash
mvn -pl application spring-boot:run
```

This is the URL that starts a loan approval:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

5000 is below the configured limit, so the risk assessment is skipped and the log shows a
workflow which decides without it:

```
Loan approval '4d0e…' started
Credit rating of loan approval '4d0e…' is 50, risk assessment not required
Loan approval '4d0e…' received 3 of 3 documents
Signatures of loan approval '4d0e…' are complete
Loan approval '4d0e…' was approved on its documents, without a risk report
```

An amount of 30000 is above the limit, so the second section runs and writes its object:

```
Credit rating of loan approval '92a5…' is 100, risk assessment required
Loan approval '92a5…' received 3 of 4 documents
Signatures of loan approval '92a5…' are incomplete
Collateral of loan approval '92a5…' is worth 12000, needed are 15000
Debt ratio of loan approval '92a5…' is 60%
Loan approval '92a5…' was rejected (collateral 12000, debt ratio 60%)
```

The result of a run is at

```
http://localhost:8080/api/loan-approval/{loanRequestId}
```

For a small loan the risk assessment is missing from that answer. The whole blueprint is about
that state. All the numbers the two sections use are in the module's own configuration
(`loan-approval/src/main/resources/loan-approval/loan-approval.yaml`).

You see nothing about identifiers at startup. The BPMS profiles of this blueprint set
`name-clash-avoidance: use-prefix`. So VanillaBP puts the workflow module ID in front of every
identifier before it reaches the engine, and takes it off again on the way back. The BPMN
files, the business code and the rest of the configuration keep the plain names. No tenant is
involved, which matters on a BPMS licensed per tenant.
[The wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided)
explains the modes and what each of them costs.

Camunda 7 serves its own web applications, and the `camunda7` profile of this blueprint
configures a user for them. They show the two shapes side by side: the call activity has a
process instance of its own, and the embedded subprocess is a box inside the loan approval.
Where they are served and how to log in is in the
[adapter's wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Cockpit-Tasklist-and-Admin).

## How it works

|                                            File                                             |                                     Role                                     |
|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn`  | the two sections and the gateway asking whether the second one ran           |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/document_check.bpmn` | the called process of the first section                                      |
| `.../loanapproval/model/Aggregate.java`                                                     | one sub-object per section, and the boolean getters the models read          |
| `.../loanapproval/model/DocumentCheck.java`                                                 | what the first section found                                                 |
| `.../loanapproval/model/RiskAssessment.java`                                                | what the second section found, absent when it did not run                    |
| `.../loanapproval/Service.java`                                                             | the business code: the first task of a section creates that section's object |
| `.../loanapproval/WorkflowTaskHandler.java`                                                 | the tasks of both sections, wired by one `@WorkflowService`                  |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                            | one run per path, plus the getters asked on an aggregate without any section |

The order of events for a big loan: the rating task answers the two questions the gateways ask.
The call activity runs the document check. The gateway sends the loan into the embedded
subprocess, and the two tasks there build the risk assessment. The gateway after it reads
`${riskAssessed}` and picks the decision task which uses the risk report. A small loan takes
the short way around the subprocess and ends up at the other decision task.

The sub-objects are embedded, not tables of their own. That is what makes one of them absent.
For a workflow which skipped the section, all of its columns are empty. An embedded object
whose columns are all empty comes back as nothing. A section object stored in a table of its
own behaves the same way, and so does a document with a missing field.

Nothing of the two sections reaches the BPMS (business process management system). `Aggregate`
carries `@NoSyncWithBPMS`, and the four getters the models read carry `@SyncWithBPMS`. So the
engine holds four booleans and the aggregate's ID. The numbers behind them stay in the
application. That is also why only the data model decides their types.

## Documentation

- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): one aggregate per business case, and the getters a model reads
- [Sharing workflow-aggregate data](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#fine-grained-control-over-attributes-synchronized-to-the-bpms): `@SyncWithBPMS`, `@NoSyncWithBPMS`, and what a BPMS gets to see
- [Call activities](https://github.com/vanillabp/spi-for-java#call-activities): when a section becomes a process of its own
- [Wire up a process](https://github.com/vanillabp/spi-for-java#wire-up-a-process): `@WorkflowService`, `@BpmnProcess` and what `secondaryBpmnProcesses` is for
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: how that engine scopes the variables of a section

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
