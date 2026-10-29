![Header](./readme/vanillabp-headline.png)

# Service tasks

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

In a service task, a process asks your application to do something. This blueprint answers the
question which comes right after wiring one up: what happens when the work does not simply
succeed? A provider cannot be reached, or its answer means the business case is over. The two
look similar in code. For the process they could hardly be more different.

## What this blueprint shows

![The loan approval process, with an error boundary event on the service task](docs/loan_approval.png)

The loan approval of the base blueprint, but the service task now asks a rating provider. A
provider can behave in three ways. Each one leaves the workflow somewhere else:

- It answers, and the rating is good enough. The handler returns, VanillaBP saves the
  aggregate, and the process goes on along the regular sequence flow.
- It does not answer. `CreditRatingUnavailableException` leaves the handler, the transaction is
  rolled back, and the BPMS (business process management system) delivers the task again. The
  model knows nothing about this, and it should not. A provider with a bad minute is not a
  business case.
- It answers, but the rating is below the minimum. The handler writes the rejection onto the
  aggregate and throws `TaskException`. The workflow leaves the task through the error boundary
  event. What the handler wrote before throwing is saved anyway.

The third one is the surprising one, so it is worth spelling out. A `TaskException` is not a
failure. It is a business outcome which the BPMN models. So VanillaBP commits the transaction,
and the process takes the error path. Every other exception is a failure, and failures leave no
trace.

Two more things this blueprint carries:

- The rating provider is an input mapping of the BPMN task (`ratingProvider`), bound to a
  `@TaskParam` of the handler. The model decides which provider is asked. It is not compiled
  in.
- `Service#assessCreditRating` returns at once if the aggregate already holds a rating. A
  remote BPMS may deliver a task more than once, and asking a provider twice may cost money.
  The state of the aggregate decides whether something already happened, never a counter of
  calls.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-springboot):

|                  File                   |                                                What is different                                                 |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`                    | an error boundary event on the service task, plus the input mapping `ratingProvider`                             |
| `CreditRatingClient.java`               | new: the port to the surrounding system the task talks to                                                        |
| `LocalCreditRatingClient.java`          | new: a stand-in provider, so the blueprint runs without one                                                      |
| `CreditRatingUnavailableException.java` | new: the technical failure the BPMS retries                                                                      |
| `Service.java`                          | asks the provider, rejects a rating below the minimum with `TaskException`, and returns early if a rating exists |
| `WorkflowTaskHandler.java`              | takes the provider as `@TaskParam`. No `@Transactional`                                                          |
| `Aggregate.java`                        | `ratedBy` and `rejectionReason` in addition to the rating                                                        |
| `LoanApprovalIT.java`                   | one test per outcome, with a simulator standing in for the provider                                              |

The one difference worth understanding is the missing `@Transactional`. VanillaBP runs a task
handler in a transaction it owns, and commits it for a `TaskException` on purpose. A
transaction declared by the application would roll that back. It would throw away the rejection
which the process is about to react to.

You do not have to remember that. An annotation on the handler or its class fails the boot with
a message which names the method. One on a bean the handler calls fails the task while it runs.
In the past such an annotation made a rejected loan look untouched in the database. Now it
stops the application instead.

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
      # Camunda 8.10 can lose a leased job for a whole job-timeout. Until Camunda fixes that,
      # the adapter advises against a lease. The key has no default:
      # https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/wiki/Configuration#a-job-somebody-else-holds
      job-lease: do-not-use
```

The blueprint does not lease its jobs. On 8.10 the gateway of the cluster can lose a leased
job when a long poll ends, and the job then waits for a whole `job-timeout`. Until Camunda
fixes that, the adapter advises `do-not-use`, and moving to `use` later costs nothing. Why is
[decision 88 of the Camunda 8 adapter](https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/blob/main/DECISIONS.md#88-until-camunda-fixes-its-gateway-the-adapter-advises-against-a-lease-and-its-own-tests-keep-one).

The file is loaded because the Maven profile `camunda8` sets the Spring profile of the same
name. So you choose the engine once, on the Maven command line. The build, the tests and
`spring-boot:run` all follow it.

[Operaton](https://operaton.org), the open source successor of Camunda 7, is embedded as well
and needs nothing else either. The BPMN files are the ones of Camunda 7, because Operaton reads
their `camunda:` attributes:

```bash
mvn install verify -Poperaton
```

[CIB seven](https://cibseven.org), a fork of Camunda 7 made by CIB software, is embedded as
well and needs nothing else either. The BPMN files are the ones of Camunda 7, because CIB seven
reads their `camunda:` attributes:

```bash
mvn install verify -Pcibseven
```

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

The stand-in provider refuses the first request for every loan request. So the first thing in
the log is the retry:

```
Loan approval '0f7c…' started
Rating provider 'acme-rating' is not answering for loan approval '0f7c…'. The BPMS will retry the task.
Credit rating of loan approval '0f7c…' is 50, awarded by 'acme-rating'
Show the result -> http://localhost:8080/api/loan-approval/0f7c…
```

On Camunda 8 the model has to ask for that retry. Its `zeebe:taskDefinition` writes
`retries="3"`. Without the attribute, the adapter fails the job with no retries left, so the
first refusal would be an incident.

Ask for a small amount to see the other outcome. A rating of 5 is below the configured minimum
of 10. So the loan is rejected, and the workflow ends through the error boundary event:

```
http://localhost:8080/api/loan-approval/start?amount=500
```

```
Credit rating of loan approval '4b21…' is 5, awarded by 'acme-rating'
Loan approval '4b21…' is rejected: rating 5 is below the minimum of 10
```

Opening the result URL shows the aggregate with the rejection reason on it. That is the point:
the handler threw, and the state survived.

Both switches are in the module's own configuration
(`loan-approval/src/main/resources/loan-approval/loan-approval.yaml`). Turn
`fail-first-rating-attempt` off for a run without the retry. Or move `minimum-rating` to decide
which amounts are rejected.

Camunda 7 serves its own web applications, and the `camunda7` profile of this blueprint
configures a user for them. The
[adapter's wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Cockpit-Tasklist-and-Admin)
says where they are served and how to log in.

## How it works

|                                          File                                          |                                                           Role                                                            |
|----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: service task with an input mapping and an error boundary event catching `loan-rejected`                      |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | the `@WorkflowTask` method. It takes the aggregate and the `@TaskParam`, calls `Service` and lets every exception through |
| `.../loanapproval/Service.java`                                                        | the work behind the task: ask the provider, rate, reject with `TaskException`                                             |
| `.../loanapproval/CreditRatingClient.java`                                             | the port to the surrounding system, so a test can replace it                                                              |
| `.../loanapproval/LocalCreditRatingClient.java`                                        | the stand-in provider, refusing the first request per loan request                                                        |
| `.../loanapproval/model/Aggregate.java`                                                | rating, provider and rejection reason, which is all the state there is                                                    |
| `loan-approval/src/test/.../CreditRatingSimulator.java`                                | the provider as the test needs it: it records requests and can be told to refuse them                                     |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | one test per outcome: rated, retried, rejected                                                                            |

The order of events: `ApiController` calls `Service#request`, which builds the
aggregate and tells `Workflow` that a loan was requested. `Workflow#loanRequested` calls
`ProcessService#startWorkflow`, so aggregate and workflow are created in one transaction.
The BPMS reaches the service task and calls `WorkflowTaskHandler#retrieveCreditRating` with
the aggregate and the mapped provider, which hands over to `Service#assessCreditRating`.
From there the three outcomes above apply, and the aggregate is saved unless the handler
failed technically.

The tests wait instead of asserting at once. A BPMS runs tasks in transactions of its own, and
a retry needs a moment to come around. Asserting right after starting a workflow would pass on
an embedded engine and fail on a remote one.

## Documentation

- [What happens when my handler throws?](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks#what-happens-when-my-handler-throws): the three outcomes, as a contract that holds on every BPMS
- [The execution contract](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks#the-execution-contract): who loads, who saves and who owns the transaction
- [Parameters](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks#parameters): `@TaskParam` and everything else a handler may ask for
- [Wire up a task](https://github.com/vanillabp/spi-for-java#wire-up-a-task): the annotations used in `WorkflowTaskHandler.java`
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: how often a failed task is retried, and how long it waits

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
