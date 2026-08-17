# persistence-mongodb

The workflow aggregate lives in MongoDB, and so do the two stores VanillaBP needs itself: the
phase-two outbox and the log of delivered tasks. No data source anywhere. A delta on top of
`module-single`, changing nothing but the persistence.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names:

|           Name            |                                   Where it occurs                                   |
|---------------------------|-------------------------------------------------------------------------------------|
| `LOAN_APPROVAL`           | the collection of the aggregate, named in `@Document`                               |
| `mongoTransactionManager` | the bean of `config/MongoTransactions`, without which the application does not boot |

**The rule this blueprint is built on:** a workflow needs three stores, and a MongoDB
application moves all three. The aggregate is yours, the phase-two outbox and the delivery log
are VanillaBP's, and they follow the aggregate's repository. Keeping a data source "for the
framework" means the persistence was only half moved.

## Core files

|                                     File                                      |                                                  Why it matters                                                  |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`           | `@Document` with the natural ID as `@Id`. No column declarations, a document database asks for none              |
| `loan-approval/src/main/java/.../loanapproval/model/AggregateRepository.java` | a `MongoRepository`. This is what attributes the outbox and the delivery log to MongoDB as well                  |
| `loan-approval/src/main/java/.../loanapproval/config/MongoTransactions.java`  | the `MongoTransactionManager` the platform does not define. Needs a replica set; without the bean the boot fails |
| `loan-approval/src/test/java/.../MongoDbForTests.java`                        | the MongoDB of the tests, one container per JVM, started as a replica set                                        |
| `loan-approval/src/test/java/.../loanapproval/LoanApprovalIT.java`            | the happy path, plus the proof that a rolled-back start leaves neither aggregate nor outbox entry behind         |
| `application/src/main/resources/application.yaml`                             | the MongoDB URI and the cluster address. No data source, in no module                                            |

## Boilerplate files

|                                  File                                   |                                             Purpose                                             |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the Spring Boot parent, the VanillaBP BOM import and the single BPMS profile                    |
| `loan-approval/pom.xml`                                                 | `vanillabp-spring-boot-support` and `spring-boot-starter-data-mongodb`, never an adapter        |
| `application/pom.xml`                                                   | `vanillabp-spring-boot-integration` and the BPMS adapter, the only place a BPMS is named        |
| `loan-approval/src/test/resources/application.yaml`                     | the cluster address of the module's own test                                                    |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the module's own configuration, loaded by its file name                                         |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: waits for workflow progress                                 |
| `loan-approval/src/test/java/.../TestApplication.java`                  | the minimal application booting the module for its test                                         |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                                  |
| `application/src/test/java/.../MongoDbForTests.java`                    | the same container class again: the smoke test boots the application, so it needs a MongoDB too |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`            | what the application tells the process; the only class using `ProcessService`                   |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | what the process tells the application; contains no business logic                              |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`       | GET endpoints operating the process                                                             |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model                       |

`WorkflowModuleTest`, `TestApplication` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged. Everything specific to the use case belongs into the test
extending `WorkflowModuleTest`, never into the base class.

## Adding this blueprint to an existing project

1. Build `module-single` first, or apply this to an existing workflow module. Everything except
   the persistence is that blueprint unchanged.
2. Replace `spring-boot-starter-data-jpa` with `spring-boot-starter-data-mongodb` in the
   workflow module, and remove the database driver from the application. If a data source stays
   behind, the framework's own stores stay relational and the application ends up with two
   databases.
3. Map the aggregate as a document: `@Document(collection = "...")`, the natural ID as `@Id`,
   no column annotations. Keep the ID a business identifier.
4. Make the repository a `MongoRepository`. Nothing else attributes the phase-two outbox and the
   delivery log to MongoDB - the outbox is chosen per aggregate, along the persistence of that
   aggregate.
5. Define a `MongoTransactionManager` bean. The platform defines none, and without one the boot
   ends with a message naming this remedy, a `TransactionRunner` bean and a
   `TransactionRunnerAware` for the aggregate. Only put it into the workflow module while the
   application has ONE database; with several, define it in the application and attribute each
   aggregate through a `TransactionRunnerAware` bean.
6. Run MongoDB as a replica set. Transactions need one, a standalone deployment fails every
   transactional write, and VanillaBP warns about it at startup. Read the startup line naming
   the transaction each aggregate is processed in - it is the fastest check that steps 4 and 5
   worked.
7. Use the `camunda8` profile or another remote BPMS. An embedded engine needs a relational
   database, so it does not fit an application without one; combining them means the engine and
   the aggregate commit separately.
8. Copy `MongoDbForTests` into every Maven module which boots the application in a test, and
   copy `LoanApprovalIT` including `aFailedStartLeavesNothingBehind`. A test which only walks
   the happy path does not show what this blueprint is about.

## Verifying

```bash
bin/camunda8_cluster.sh start   # in the monorepo, or bring your own cluster
mvn install verify
```

`camunda8` is the only profile of this blueprint and it is active by default. Docker is
required: the tests start MongoDB as a container, and the cluster runs in containers as well.

Both tests of `LoanApprovalIT` have to pass. `theServiceTaskFillsTheAggregate` proves the wiring
between BPMN and code, `aFailedStartLeavesNothingBehind` proves the aggregate and the outbox
entry share one transaction - if the second one fails while the first passes, the MongoDB
transaction is not in place: check the manager bean and whether the deployment is a replica set.
`ApplicationSmokeTest` passing means the application boots with the module on the classpath.

Do not report success without having run this.
