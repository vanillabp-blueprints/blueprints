![Header](./readme/vanillabp-headline.png)

# Workflow aggregates without a repository

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

VanillaBP loads and saves the workflow aggregate on the application's behalf, so it has to
know how that is done. Usually a repository answers that question. This blueprint removes the
repository: the aggregate is an active record and stores itself, which leaves it as the only
class on the persistence path. A delta on top of `module-single`.

The interesting part of this blueprint is what is not in it. Nothing configures the
persistence, and nothing is injected to reach it.

## What this blueprint shows

![The loan approval process](docs/loan_approval.png)

The loan approval of the base blueprint, unchanged: one service task, started through a GET
request, and an aggregate the task fills. The process is not the point here, the way the
aggregate is stored is.

`Aggregate` extends `PanacheEntityBase`, and that is the whole persistence layer:

```java
@Entity
@Table(name = "LOAN_APPROVAL")
public class Aggregate extends PanacheEntityBase {

  @Id
  private String loanRequestId;
  ...
}
```

No repository, no `AggregatePersistenceAware`, no property naming either of them. VanillaBP
asks the aggregate which idiom it is written in while the application is built, and says what
it found, one line per aggregate:

```
Using VanillaBP's Hibernate ORM Panache active record persistence for workflow aggregate
'blueprint.workflowmodule.loanapproval.model.Aggregate'
```

That line is worth knowing about, because the decision is made per aggregate rather than per
application, and the order is fixed: an `AggregatePersistenceAware` written by the application
wins over everything, a repository for that aggregate wins over the active record, and Spring
Data repositories answer last. An application may therefore mix idioms, and adding a
repository for this aggregate later moves it onto that repository without a word of
configuration. The log line is how you see which way it went - and if it names a repository
you thought you had deleted, that is the answer to why nothing changed.

**The price, and where it is paid.** A repository is a bean, so it is a place where things can
be declared - a transaction, most of all. Without it the application reads through the static
finder of the aggregate, and that needs a transaction or an active request context of its own:

- `Service#getLoanApproval` carries `@Transactional`, as it does in the base blueprint. Here it
  is not a formality but the only place left where the transaction can be declared.
- the integration test reads in a transaction of its own per poll, which is what the shared
  test harness does anyway. Without it, the finder would have nothing to read from.

Nothing changes for the `@WorkflowTask` methods. VanillaBP owns the transaction of a task,
loads the aggregate before the method and saves it afterwards, so a handler declaring a
transaction is as wrong here as everywhere else, and VanillaBP still fails the boot over it.

**Starting a workflow on a remote engine is the interesting case.** It happens in two phases:
the application's transaction stores the aggregate and the intent to start, then VanillaBP
talks to the engine and writes the result back, on a thread of its own. Nothing of the
application is on the stack at that moment, so with no repository there is no class of yours
left to hang a transaction on. VanillaBP opens one itself, which is why an aggregate can be an
active record at all. `LoanApprovalIT#theStartedWorkflowKeepsItsAggregate` is that assertion,
and it only means something on a remote engine - run this blueprint with `-Pcamunda8`, not
only with the embedded one.

An active record is an ordinary aggregate in every other respect, including the way two
branches of one workflow can overwrite each other's writes. That is
[`persistence-parallel-branches`](https://github.com/vanillabp-blueprints/persistence-parallel-branches-quarkus)
and [the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate),
not repeated here.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|                       File                        |                                         What is different                                         |
|---------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `.../loanapproval/model/AggregateRepository.java` | deleted. There is no repository, and nothing replaces it                                          |
| `.../loanapproval/model/Aggregate.java`           | extends `PanacheEntityBase` and carries the one finder the application needs, `byId`              |
| `.../loanapproval/Service.java`                   | injects no repository; the reading method calls `Aggregate.byId` inside its own transaction       |
| `loan-approval/src/test/.../LoanApprovalIT.java`  | reads through `Aggregate::byId`, and asserts that a workflow started remotely keeps its aggregate |

Everything else is the base blueprint, file for file: the process, the wiring classes, the
API, the module's own configuration, the test harness.

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

That is not the whole story for this blueprint, though. The assertion about a workflow started
on a remote engine only means something there:

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
      # Nothing else is needed: this adapter keeps workflow modules apart by nothing at all
      # ('name-clash-avoidance: none') unless told otherwise, because a cluster started from
      # the stock image has multi-tenancy switched off and rejects a tenant per module. The
      # adapter warns about it while booting - with one workflow module the identifiers are
      # unique anyway. Set 'name-clash-avoidance: use-prefix' to have VanillaBP prefix them.
```

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

Building the application is where the line about the persistence appears, so it shows up in
that command as well as in `mvn install`.

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

Opening that URL reads the aggregate through its own finder and shows the credit rating the
service task wrote.

## How it works

|                                          File                                          |                                              Role                                              |
|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `.../loanapproval/model/Aggregate.java`                                                | the workflow aggregate, a JPA entity which is also its own persistence, plus the finder `byId` |
| `.../loanapproval/Service.java`                                                        | the business code; its reading method owns the transaction the finder needs                    |
| `.../loanapproval/Workflow.java`                                                       | what the application tells the process; the only class using `ProcessService`                  |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | what the process tells the application: `@WorkflowService`, `@WorkflowTask`, calls `Service`   |
| `.../loanapproval/ApiController.java`                                                  | the GET endpoints operating the process                                                        |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: start event, service task, end event                                              |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | starts real workflows, reads the aggregate statically, and covers the remote start             |
| `loan-approval/src/test/.../WorkflowModuleTest.java`                                   | the base class it inherits from: a fresh transaction per poll, identical in every blueprint    |
| `application/src/main/resources/application.yaml`                                      | the database, and nothing about the workflow or its persistence                                |

What happens when a loan is requested is the same as in the base blueprint, except for who
touches the database. `Service#initiateLoanApproval` builds the aggregate and tells `Workflow`
that a loan was requested; `ProcessService#startWorkflow` saves the aggregate - through
Panache's operations rather than through a repository - and starts the workflow in the same
transaction. When the BPMS reaches the service task, VanillaBP loads the aggregate, calls
`WorkflowTaskHandler#retrieveCreditRating`, and saves it again, all inside the transaction it
owns for that task.

There is no class of the application involved in any of that, which is the point and also the
limit of the idiom: everything the application itself wants to read has to bring a transaction
along. Two places do, and they are the two places which read.

## Documentation

- [Persisting workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#persisting-workflow-aggregates): the idioms recognised, the order they are resolved in, and what ends the build instead
- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): why there are no process variables, and what an aggregate is for
- [Two writers on one aggregate](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate): the collision an active record has as well, and the four ways to deal with it
- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
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

        https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the
License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. See the License for the specific language governing permissions
and limitations under the License.
