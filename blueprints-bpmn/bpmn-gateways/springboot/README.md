![Header](./readme/vanillabp-headline.png)

# Gateways

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

A process that always runs straight through does not need a diagram. This blueprint is
about the first fork: how a gateway decides which way to go when there are no process
variables to decide on.

## What this blueprint shows

![The loan approval process, with an exclusive gateway and three branches](docs/loan_approval.png)

The loan approval of the base blueprint, with three ways out instead of one. What is worth
looking at is where the decision is made:

- The service task rates the request and writes what that rating counts as onto the
  workflow aggregate: `acceptable`, `review` or `too-low`.
- The gateway routes on exactly that attribute. Its conditions name `ratingBand`, nothing
  else, and each branch ends in a task of its own.
- The third branch is the gateway's **default flow**. It is taken when no condition holds,
  which is what keeps a workflow from getting stuck at a gateway that fits nowhere.

Two rules come out of that, and they are the point of this blueprint:

**Conditions read the workflow aggregate.** There are no process variables in VanillaBP, so
a condition names an attribute of a Java class you can open, with a type and a comment.
What the process is allowed to decide on is therefore visible in the aggregate rather than
scattered over the model.

**The model routes, the business code decides.** The comparison `rating >= 30` lives in
`Service`, fed by configuration, not in the BPMN. A threshold in a condition looks harmless
until it has to change: then it is a new process version, deployed, with running instances
left on the old one. Moving it is a line of configuration here.

And one rule the blueprint learned the hard way: **conditions must not overlap.** An earlier
version of this model routed on two booleans, `riskAcceptable` and `worthAManualReview`,
which are both true for a good rating. Camunda 7 then took the first branch and Camunda 8
the second, both of them entitled to. One attribute holding one of three values cannot have
that problem.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|            File            |                                    What is different                                     |
|----------------------------|------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`       | an exclusive gateway with two conditional sequence flows and a default flow              |
| `Aggregate.java`           | `ratingBand`, the one attribute the gateway reads, plus the `outcome` the branches write |
| `Service.java`             | turns the rating into a band, and one method per branch                                  |
| `WorkflowTaskHandler.java` | a `@WorkflowTask` method per branch                                                      |
| `loan-approval.yaml`       | the two thresholds the bands are derived from                                            |
| `LoanApprovalIT.java`      | one test per branch, steered by the amount                                               |

The conditions differ between the two BPMS, and that is the one place where they have to:
Camunda 7 evaluates `${ratingBand == 'acceptable'}`, Camunda 8 the FEEL expression
`=ratingBand = "acceptable"`. Both read the same attribute of the same aggregate, and no
line of Java knows about either.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run and be pointed at. Start one, then
add its address to `application/src/main/resources/application.yaml` and to
`loan-approval/src/test/resources/application.yaml`:

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

Start the application:

```bash
mvn -pl application spring-boot:run
```

Booting logs a warning per workflow module: both Camunda adapters start out with
`name-clash-avoidance: none`, so nothing keeps the identifiers of one workflow module apart
from those of another, and the adapter asks for a decision instead of picking one. One module
cannot collide with itself, so this blueprint leaves it at that. Answering the question is one
property, `vanillabp.adapters.<id>.accept-unscoped-identifiers: true`, and the modes a BPMS
offers are in
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules#how-name-clashes-are-avoided).

The amount decides which branch is taken, so this is the URL to play with:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

An amount of 5000 is a rating of 50, which is at or above the configured minimum of 30:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50, which counts as 'acceptable'
Loan approval '0f7c…' was approved
```

1500 gives a rating of 15 - below the minimum, but a person should still look at it:

```
Credit rating of loan approval '4b21…' is 15, which counts as 'review'
Loan approval '4b21…' goes to a manual review
```

300 gives a rating of 3, which fits no condition, so the default flow is taken:

```
Credit rating of loan approval '9d02…' is 3, which counts as 'too-low'
Loan approval '9d02…' was rejected
```

Both thresholds are in the module's own configuration
(`loan-approval/src/main/resources/loan-approval/loan-approval.yaml`). Move them and the
same amounts end up somewhere else, without the model being touched.

While the application runs on Camunda 7, Camunda's own web applications are served at

```
http://localhost:8080/camunda
```

Log in with `demo` / `demo`. Cockpit shows which branch an instance took, which is the
quickest way to see a gateway decision from the outside. The user comes from
`application/src/main/camunda7/resources/camunda7-webapps.yaml` and exists so that the
blueprint can be operated without setting one up; an application with an identity provider
of its own leaves that section out.

The Camunda 8 profile ships neither the dependency nor that file. Its tooling is part of
the cluster, and the file names a Camunda 7 adapter id, which VanillaBP would rightly
refuse to start with.

## How it works

|                                          File                                          |                                              Role                                              |
|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: one exclusive gateway, two conditions on `ratingBand` and a default flow          |
| `.../loanapproval/Service.java`                                                        | turns the rating into a band using the configured thresholds, and does the work of each branch |
| `.../loanapproval/model/Aggregate.java`                                                | `ratingBand`, which the conditions read, and `outcome`, which says where the workflow ended    |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | one `@WorkflowTask` method per branch, each of them forwarding to `Service`                    |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                    | the thresholds, which is where a number like "30" belongs                                      |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | one test per branch, each one steered by the amount alone                                      |

The order of events: `Service#assessCreditRating` writes the rating and the band, VanillaBP
saves the aggregate when the task handler returns, and the BPMS then evaluates the
conditions of the gateway. On an embedded engine the condition reads the aggregate directly;
on a remote one it reads what VanillaBP shared with the cluster when the task was completed.
Neither is visible in the model, which is why the same BPMN works on both once the
expression syntax matches the engine.

A gateway needs nothing from the application beyond the attribute it reads. There is no
`ProcessService` call in this blueprint apart from starting the workflow, and no handler
knows that a gateway exists.

## Documentation

- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables, and what a condition can read
- [Wire up an expression](https://github.com/vanillabp/spi-for-java#wire-up-an-expression): how an expression in the model reaches your data
- [Sharing workflow-aggregate data](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#fine-grained-control-over-attributes-synchronized-to-the-bpms): what a remote engine gets to see, and how to keep an attribute out of it
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: the expression language of that engine

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
