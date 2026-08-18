![Header](./readme/vanillabp-headline.png)

# Workflow aggregates in MongoDB

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

An application does not need a relational database to run VanillaBP. This blueprint is the
proof: the same loan approval as the base blueprint, with the workflow aggregate in MongoDB and
no data source anywhere in the project. A delta on top of `module-single`.

What makes it more than a swapped repository is the count: a workflow needs **three** stores,
and all three of them move.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The loan approval of the base blueprint, unchanged: one service task, started through a GET
request, and an aggregate the task fills. The process is not the point here, the database is.

The three stores of a workflow, and where they are:

|           Store            |                     What it is for                      |        Where it lives        |
|----------------------------|---------------------------------------------------------|------------------------------|
| the workflow aggregate     | the state of the business case, your own data           | collection `LOAN_APPROVAL`   |
| the phase-two outbox       | makes a workflow start on a remote BPMS survive a crash | `vanillabp-phase-two-outbox` |
| the log of delivered tasks | keeps a repeated delivery from running a handler twice  | `vanillabp-task-deliveries`  |

Only the first one is written by this application's code. The other two are VanillaBP's, and
they follow the aggregate: putting the aggregate on a MongoDB repository is what moves them, no
property says it. That is why an application which stores its aggregate in MongoDB but keeps a
data source around for "the framework" has misunderstood something - there is nothing left to
put in there.

**And it costs no extra class.** The platform's transaction always exists here, MongoDB Panache
binds a MongoDB transaction to it, and VanillaBP's own MongoDB stores write through that same
session. Booting says which transaction each aggregate is processed in:

```
Workflow aggregate 'blueprint.workflowmodule.loanapproval.model.Aggregate' (BPMN process
'loan_approval' of workflow module 'loan-approval') is processed in the transaction of:
the JTA transaction of Quarkus
```

So loading the aggregate, the `@WorkflowTask` method, saving it, the outbox entry and the
delivery record either all commit or none of them do. The condition is the deployment:
**MongoDB runs transactions only as a replica set.** The dev services take care of it while
developing and testing; in production it is on you, and VanillaBP warns at startup when the
server it talks to is a standalone one.

**Camunda 7 is missing on purpose.** Its engine is embedded and needs a relational database,
which is exactly what this blueprint does not have. Running the two together is possible, and
then the engine and the aggregate commit separately - a compromise worth knowing about, but not
what a blueprint about MongoDB should demonstrate. So this is the first blueprint with a single
engine, and a cluster has to run for every build.

**The counter-check is in the test**, because the happy path proves nothing here:
`LoanApprovalIT#aFailedStartLeavesNothingBehind` rolls the transaction back after a workflow was
started and asserts that neither the aggregate nor the outbox entry survived. Before the
framework taught its MongoDB stores to write through the same session, that assertion failed on
its second half.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|                       File                        |                                   What is different                                    |
|---------------------------------------------------|----------------------------------------------------------------------------------------|
| `.../loanapproval/model/Aggregate.java`           | a document in a collection instead of an entity in a table, and no column declarations |
| `.../loanapproval/model/AggregateRepository.java` | a MongoDB Panache repository; this is what attributes the other two stores as well     |
| `application/src/main/resources/application.yaml` | a MongoDB database instead of a data source, and the address of the cluster            |
| `loan-approval/src/test/.../LoanApprovalIT.java`  | the counter-check that a rolled-back start leaves nothing behind                       |
| `pom.xml`, `*/pom.xml`                            | `quarkus-mongodb-panache` instead of Hibernate, no H2, and only the `camunda8` profile |

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
The tests need no MongoDB of their own either: the dev services start one and run it as a
replica set, which is why nothing in the configuration says where the database is.

Start the application:

```bash
mvn -pl application quarkus:dev
```

Booting logs a warning per workflow module: both Camunda adapters start out with
`name-clash-avoidance: none`, so nothing keeps the identifiers of one workflow module apart
from those of another, and the adapter asks for a decision instead of picking one. One module
cannot collide with itself, so this blueprint leaves it at that. Answering the question is one
property, `vanillabp.adapters.<id>.accept-unscoped-identifiers: true`, and the modes a BPMS
offers are in
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

The collections are worth a look while it runs. `LOAN_APPROVAL` holds the aggregate,
`vanillabp-phase-two-outbox` the entry which made the start crash-safe, marked `DONE` after it
was dispatched and deleted later, and `vanillabp-task-deliveries` the record of the task the
BPMS handed over.

Going to production means naming the address of a MongoDB, and it has to be a replica set:

```yaml
quarkus:
  mongodb:
    connection-string: mongodb://localhost:27017/?replicaSet=rs0
```

## How it works

|                                          File                                          |                                     Role                                      |
|----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `.../loanapproval/model/Aggregate.java`                                                | the workflow aggregate, a document keyed by the loan request ID               |
| `.../loanapproval/model/AggregateRepository.java`                                      | how it is stored and loaded, for the application and for VanillaBP            |
| `.../loanapproval/Service.java`                                                        | the business code; unchanged from the base blueprint                          |
| `.../loanapproval/Workflow.java`                                                       | what the application tells the process; the only class using `ProcessService` |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | what the process tells the application: `@WorkflowService`, `@WorkflowTask`   |
| `loan-approval/src/main/resources/loan-approval/processes/camunda8/loan_approval.bpmn` | the process: start event, service task, end event                             |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | the happy path, and the proof that a rolled-back start leaves nothing behind  |
| `application/src/main/resources/application.yaml`                                      | the MongoDB database, and no data source                                      |
| `application/src/main/resources/application-camunda8.yaml`                             | the address of the cluster, loaded by the profile of that engine              |

The order of events is the one of the base blueprint. What changed is what the transaction
around it covers. Starting a workflow on a remote BPMS happens in two phases: the application's
transaction stores the aggregate and an outbox entry, and after the commit VanillaBP tells the
engine and writes the result back, on a thread of its own where it opens a transaction itself.
Both of those now carry the MongoDB session, so the atomicity the outbox promises holds in a
MongoDB application as well - which is what the second test pins down.

## Documentation

- [Persisting workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#persisting-workflow-aggregates): the idioms recognised, and the order they are resolved in
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables, and the table of what a crash leaves behind per store
- [Configure the transaction outbox](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#configure-the-transaction-outbox): the MongoDB store, its collection, its properties and the replica-set condition
- [What VanillaBP remembers about delivered tasks](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#what-vanillabp-remembers-about-delivered-tasks): the third store
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
