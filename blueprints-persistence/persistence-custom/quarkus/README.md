![Header](./readme/vanillabp-headline.png)

# A persistence of your own

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

VanillaBP serves the persistence technologies the platform knows. This blueprint is for the case
it does not know yours: an event store, a ledger, a message producer, a service behind an API.
The application then answers four questions itself, and this is what the answers look like. A
delta on top of `module-single`.

There is no database here at all, not even for the framework. The store is a map, so the code
stays about the contract instead of about a technology.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The loan approval of the base blueprint, unchanged: one service task, started through a GET
request, and an aggregate the task fills. What changed is everything below it - and the fact
that the aggregate class carries no annotation at all is the first thing to look at.

Four beans, and none of them is optional:

|             Bean             |                          What it answers                          |           The SPI           |
|------------------------------|-------------------------------------------------------------------|-----------------------------|
| `persistence/UnitOfWork`     | in which transaction VanillaBP may do its work                    | `TransactionRunner`         |
| `persistence/AggregateStore` | how the workflow aggregate is saved and loaded                    | `AggregatePersistenceAware` |
| `persistence/PhaseTwoStore`  | where "this workflow still has to be started" is kept until it is | `PhaseTwoOutbox`            |
| `persistence/DeliveryLog`    | which task deliveries were already processed                      | `TaskDeliveryLog`           |

**Start with the unit of work.** The platform's transaction exists here whatever happens, and
that is exactly the trap: it would commit while the store of this application, which the platform
does not manage, commits on its own. So the application contributes its unit of work and VanillaBP
uses that one instead. It resolves it per aggregate: a
`TransactionRunnerAware` bean for a specific aggregate first, a plain `TransactionRunner` bean
second, the platform's own runner last. Booting names the winner:

```
Workflow aggregate 'blueprint.workflowmodule.loanapproval.model.Aggregate' (BPMN process
'loan_approval' of workflow module 'loan-approval') is processed in the transaction of:
the TransactionRunner bean
'blueprint.workflowmodule.loanapproval.persistence.UnitOfWork' of the application
```

**VanillaBP may ask the unit of work to run something right before it commits.** A remote BPMS
is asked in phase one whether an operation still makes sense - is the task still there - and that
answer can go stale before VanillaBP acts on it, so the adapter hands the check to the runner of
the aggregate. `TransactionRunner#beforeCommit` has a default which runs the check immediately,
which is what this blueprint's `UnitOfWork` uses: correct, with a window as wide as it was before.
A unit of work with its own pre-commit hook implements the method and registers the check there.

**The three stores enlist in that unit of work**, and that is the part which is easy to get
wrong. A write goes into a buffer of the running unit of work and reaches the store itself when
that unit of work commits. Writing straight into the map would look correct until the first
rollback: an aggregate of a business case which never happened, an outbox entry for a workflow
nobody wanted, a delivery record for work which was undone - and that last one is the worst,
because the repetition which would have fixed it is answered from the record and never runs.

**Where the other blueprints write `@Transactional`, this one opens the unit of work itself.**
An annotation of the platform would open a transaction this application's store is not part of,
and VanillaBP refuses to start a workflow when the unit of work it asks about is not open. So
`Service#initiateLoanApproval` brackets its work with `UnitOfWork`, and the methods a task
handler calls bracket nothing, exactly as everywhere else.

**In memory means: gone after a restart.** That is deliberate. This blueprint is the reference
for the contract, not for operations, and the test proves the contract. What a durable store
adds is named where it belongs, in the javadoc of each bean: retrying a failed dispatch with a
backoff, dispatching entries again after a restart, keeping dispatched entries for a while
instead of deleting them, blocking an entry which keeps failing, and dropping delivery records
after a retention period.

**Camunda 7 is missing on purpose.** Its engine is embedded and needs a relational database,
which is exactly what this blueprint does not have. So this is one of two blueprints with a
single engine, and a cluster has to run for every build.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|                        File                        |                                     What is different                                      |
|----------------------------------------------------|--------------------------------------------------------------------------------------------|
| `.../loanapproval/model/Aggregate.java`            | a plain class: no entity, no column, nothing naming a database                             |
| `.../loanapproval/model/AggregateRepository.java`  | deleted, together with the persistence dependency of the workflow module                   |
| `.../loanapproval/persistence/UnitOfWork.java`     | new: the transaction VanillaBP runs its work in, and the hooks the stores enlist with      |
| `.../loanapproval/persistence/AggregateStore.java` | new: how the aggregate is saved and loaded                                                 |
| `.../loanapproval/persistence/PhaseTwoStore.java`  | new: the outbox for the second phase of a workflow start                                   |
| `.../loanapproval/persistence/DeliveryLog.java`    | new: what was already delivered                                                            |
| `.../loanapproval/Service.java`                    | opens the unit of work itself instead of carrying `@Transactional`                         |
| `loan-approval/src/test/.../LoanApprovalIT.java`   | counts what VanillaBP did with the unit of work, and checks that a rollback leaves nothing |
| `pom.xml`, `*/pom.xml`                             | no persistence dependency at all, and only the `camunda8` profile                          |

Everything else is the base blueprint, file for file: the process, the wiring classes, the API,
the module's own configuration, the test harness.

## Running it

Requires a JDK 21, Docker and a Camunda 8 cluster. The monorepo brings the shortest way to a
cluster:

```bash
bin/camunda8_cluster.sh start
```

Then, in this directory:

```bash
mvn install verify
```

`camunda8` is the only profile and it is active by default, so there is no `-P` to
remember. That profile is also what loads `application-camunda8.yaml`: the Maven profile sets
the config profile of the same name the parent of whichever profile the application runs in, so the engine is named once and the build, the tests and
running the application all follow it.
There is nothing else to install: no database, no container beyond the cluster.

Start the application:

```bash
mvn -pl application quarkus:dev
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

Restart the application and the loan approval is gone, which is the honest consequence of a
store in memory.

## How it works

|                        File                        |                                         Role                                         |
|----------------------------------------------------|--------------------------------------------------------------------------------------|
| `.../loanapproval/persistence/UnitOfWork.java`     | opens, commits and rolls back the application's transaction; counts it for the test  |
| `.../loanapproval/persistence/AggregateStore.java` | saves and loads the aggregate, buffered until the unit of work commits               |
| `.../loanapproval/persistence/PhaseTwoStore.java`  | keeps the scheduled second phase and hands it to VanillaBP's router after the commit |
| `.../loanapproval/persistence/DeliveryLog.java`    | remembers processed deliveries so a repeated one is not run twice                    |
| `.../loanapproval/model/Aggregate.java`            | the workflow aggregate, a plain class                                                |
| `.../loanapproval/Service.java`                    | the business code; opens the unit of work for what the API calls                     |
| `.../loanapproval/Workflow.java`                   | what the application tells the process; the only class using `ProcessService`        |
| `.../loanapproval/WorkflowTaskHandler.java`        | what the process tells the application: `@WorkflowService`, `@WorkflowTask`          |
| `loan-approval/src/test/.../LoanApprovalIT.java`   | three tests: the happy path, the counters, and the rollback                          |

What happens when a loan is requested: `Service` opens a unit of work and tells `Workflow` that
a loan was requested. Inside it, VanillaBP saves the aggregate through `AggregateStore` and
schedules the second phase of the start through `PhaseTwoStore`, because the engine is remote
and cannot join a transaction here. The commit makes both visible and hands the scheduled call
back to VanillaBP, which talks to the engine and writes the result - in a unit of work it opens
itself, on its own thread. When the BPMS then delivers the service task, VanillaBP opens another
one, records the delivery, loads the aggregate, calls the handler and saves the aggregate again.

Three units of work for one workflow, all of them this application's. The test counts them,
which is the only way to see the difference: a workflow which simply works looks the same when
something else is committing.

## Documentation

- [Aggregate persistence](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#aggregate-persistence): the interface, how an implementation is selected, and what VanillaBP guarantees around the calls
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables, and the table of what a crash leaves behind per store
- [Configure the transaction outbox](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#configure-the-transaction-outbox): what an outbox has to guarantee, and what a store of your own has to keep
- [What VanillaBP remembers about delivered tasks](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#what-vanillabp-remembers-about-delivered-tasks): the third store and why it exists
- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
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

        https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the License.
