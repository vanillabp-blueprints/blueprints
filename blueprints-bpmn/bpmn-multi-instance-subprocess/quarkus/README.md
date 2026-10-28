![Header](./readme/vanillabp-headline.png)

# Multi-instance subprocesses

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Repeating one task is a multi-instance task. Repeating a whole section of a process, with
several tasks, a flow and maybe a wait state, is a multi-instance subprocess. This blueprint
shows one, with a second iteration nested inside it. There the question "which loop am I in?"
becomes a real question.

## What this blueprint shows

![The loan approval process, assessing every region and asking every partner](docs/loan_approval.png)

The loan approval is assessed once per region, and inside every region every partner is
asked for an offer. Two loops, four offers, two region results, one decision.

The subprocess is what repeats. Everything drawn inside it runs again per region, and no Java
says so. The tasks in it are wired like any other. That property is worth keeping: the same
handler serves whether the section runs once or fifty times.

Three kinds of task appear inside. They differ in what they need to know:

`requestPartnerOffer` sits in BOTH iterations, so it is handed an `Iteration` record built
by a resolver:

```java
@WorkflowTask
public void requestPartnerOffer(
    final Aggregate loanRequest,
    @MultiInstanceElement(resolverBean = IterationResolver.class) final Iteration iteration) {
```

A resolver gets every active multi-instance context, keyed by the BPMN id of its element, the
outermost first. It returns whatever the method wants. That is how the SPI (service provider
interface) answers nesting: one object which says what the pair means, instead of six
parameters.

`summariseRegion` belongs to the outer iteration only, and asks for it by name:

```java
@MultiInstanceElement("SubProcess_AssessRegion") final String regionId,
@MultiInstanceIndex("SubProcess_AssessRegion") final int index,
@MultiInstanceTotal("SubProcess_AssessRegion") final int total
```

**The task is not multi-instance itself, and still runs inside an iteration.** That is the
point of a multi-instance subprocess. It is also why every annotation names an element. A
method has to say which of the active iterations it asks about.

`chooseBestOffer` sits after the subprocess and knows nothing about any iteration. A result
over all regions belongs there. A result over one region's partners belongs at the end of that
region, in `summariseRegion`. Neither belongs in a task which sees one element.

What the loops go over comes from the workflow aggregate, one collection per level. Every
iteration writes ROWS, not attributes: `PartnerOffer` per region and partner, `RegionResult`
per region. Instances run next to each other, and each of them saves the whole aggregate. If
two of them write one attribute, the one which commits last wins.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|            File            |                                        What is different                                         |
|----------------------------|--------------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`       | a multi-instance subprocess, a multi-instance task inside it, and a plain task inside it as well |
| `Aggregate.java`           | a collection per level, and the rows the two loops write                                         |
| `PartnerOffer.java`        | new: one row per inner iteration, carrying the region it belongs to                              |
| `RegionResult.java`        | new: one row per outer iteration                                                                 |
| `IterationResolver.java`   | new: builds one object out of both active iterations                                             |
| `Iteration.java`           | new: what that object is                                                                         |
| `WorkflowTaskHandler.java` | a task in both loops, a task in the outer one, a task after them                                 |
| `Service.java`             | one method per step, none of them knowing about a loop                                           |
| `loan-approval.yaml`       | regions and partners, which is what decides how often each level runs                            |
| `LoanApprovalIT.java`      | asserts the rows of both levels, in no particular order                                          |

Compared to
[`bpmn-multi-instance-task`](https://github.com/vanillabp-blueprints/bpmn-multi-instance-task-quarkus),
which repeats a single task: the iteration moves to a subprocess, and a second one is nested
inside it. The resolver appears because a method now sits in two loops at once.

## Running it

Requires a JDK 21 or newer. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

To run it on another BPMS (business process management system), pick another Maven profile. Not
one line of Java changes:

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

The file is loaded because the Maven profile `camunda8` makes the config profile of the same
name the parent of the profile the application runs in. So you choose the engine once, on the
Maven command line. The build, the tests and `quarkus:dev` all follow it.

[Operaton](https://operaton.org), the open source successor of Camunda 7, is embedded as well
and needs nothing else either. The BPMN files are the ones of Camunda 7, because Operaton reads
their `camunda:` attributes:

```bash
mvn install verify -Poperaton
```

Start the application:

```bash
mvn -pl application quarkus:dev
```

You see nothing about identifiers at startup. The BPMS profiles of this blueprint set
`name-clash-avoidance: use-prefix`. So VanillaBP puts the workflow module ID in front of every
identifier before it reaches the engine, and takes it off again on the way back. The BPMN
files, the business code and the rest of the configuration keep the plain names. No tenant is
involved, which matters on a BPMS licensed per tenant.
[The wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided)
explains the modes and what each of them costs.

This is the URL that starts a loan approval:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

The log shows both loops, and the position of every call in them:

```
Loan approval '764f…' started
Credit rating of loan approval '764f…' is 50, asking 2 partner(s) in 2 region(s)
Asking partner 1 of 2 in region 1 of 2 for loan approval '764f…'
Asking partner 2 of 2 in region 1 of 2 for loan approval '764f…'
Asking partner 1 of 2 in region 2 of 2 for loan approval '764f…'
Asking partner 2 of 2 in region 2 of 2 for loan approval '764f…'
Region 'north' of loan approval '764f…' settles on 'northern-bank' at 70 basis points
Region 'south' of loan approval '764f…' settles on 'northern-bank' at 85 basis points
Loan approval '764f…' takes 'northern-bank' in region 'north' at 70 basis points, out of 2 region(s)
```

The result of a run is at

```
http://localhost:8080/api/loan-approval/{loanRequestId}
```

Add a region to `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`, and the
subprocess runs three times instead of twice. Every partner is asked in each of them. Nobody
touches the model. That is why the two lists live in configuration.

## How it works

|                                            File                                            |                                     Role                                      |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the subprocess, the task nested in it and the plain task inside the iteration |
| `.../loanapproval/IterationResolver.java`                                                  | builds one object out of every active iteration                               |
| `.../loanapproval/WorkflowTaskHandler.java`                                                | one method per task, each asking for the level it belongs to                  |
| `.../loanapproval/Service.java`                                                            | the business code, which knows regions and partners but no loops              |
| `.../loanapproval/model/Aggregate.java`                                                    | a collection per level, and the rows the iterations write                     |
| `.../loanapproval/model/PartnerOffer.java`, `RegionResult.java`                            | a row per inner and per outer iteration                                       |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                           | asserts both levels, in no particular order                                   |

The order of events: `retrieveCreditRating` writes the two collections. The engine creates one
instance of the subprocess per region. Each of those creates one instance of the offer task per
partner. `summariseRegion` runs when a region's offers are in, and `chooseBestOffer` when the
last region is done.

If the application had to know the differences between the BPMS (business process management
systems), they would show in a nested iteration. So it is worth saying that it does not have
to. Camunda 7 reports the iterations from its execution hierarchy. Camunda 8 has none, and its
adapter prepares the model while deploying, so the values arrive anyway. The Java above is the
same on both.

## Documentation

- [Multi-instance](https://github.com/vanillabp/spi-for-java#multi-instance): the three annotations, and the resolver for nested iterations
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why the collections are attributes, and what several writers do to a row
- [Wire up a task](https://github.com/vanillabp/spi-for-java#wire-up-a-task): what a `@WorkflowTask` method may be handed
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: what that engine reports about an iteration
- [What becomes of a shared value on Camunda 7](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/What-becomes-of-a-shared-value): what happens to the four collections of this aggregate there, and why an application should pick a serialization format

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
