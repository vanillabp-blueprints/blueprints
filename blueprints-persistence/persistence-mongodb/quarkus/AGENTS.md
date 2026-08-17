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

Blueprint-specific name:

|      Name       |                     Where it occurs                      |
|-----------------|----------------------------------------------------------|
| `LOAN_APPROVAL` | the collection of the aggregate, named in `@MongoEntity` |

**The rule this blueprint is built on:** a workflow needs three stores, and a MongoDB
application moves all three. The aggregate is yours, the phase-two outbox and the delivery log
are VanillaBP's, and they follow the aggregate's repository. Keeping a data source "for the
framework" means the persistence was only half moved.

## Core files

|                                     File                                      |                                                                     Why it matters                                                                     |
|-------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`           | `@MongoEntity` with the natural ID as `@BsonId`. No column declarations, a document database asks for none                                             |
| `loan-approval/src/main/java/.../loanapproval/model/AggregateRepository.java` | a `PanacheMongoRepositoryBase`. It binds a MongoDB transaction to the platform's, and it attributes the outbox and the delivery log to MongoDB as well |
| `loan-approval/src/test/java/.../loanapproval/LoanApprovalIT.java`            | the happy path, plus the proof that a rolled-back start leaves neither aggregate nor outbox entry behind                                               |
| `application/src/main/resources/application.yaml`                             | the MongoDB database and the cluster address. No data source, in no module                                                                             |

## Boilerplate files

|                                  File                                   |                                                    Purpose                                                     |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the Quarkus BOM, the VanillaBP BOM import and the single BPMS profile                                          |
| `loan-approval/pom.xml`                                                 | `vanillabp-quarkus-support`, `quarkus-mongodb-panache` and the index of the module's classes, never an adapter |
| `application/pom.xml`                                                   | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named                           |
| `loan-approval/src/test/resources/application.yaml`                     | the database name and the cluster address of the module's own test                                             |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the module's own configuration, loaded by its file name                                                        |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: a fresh transaction per poll                                               |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                                                 |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`            | what the application tells the process; the only class using `ProcessService`                                  |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | what the process tells the application; contains no business logic                                             |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`       | GET endpoints operating the process                                                                            |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model                                      |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy them
unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the base class is
not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Build `module-single` first, or apply this to an existing workflow module. Everything except
   the persistence is that blueprint unchanged.
2. Replace `quarkus-hibernate-orm-panache` with `quarkus-mongodb-panache` in the workflow module,
   and remove the JDBC driver from the application. If a data source stays behind, the
   framework's own stores stay relational and the application ends up with two databases.
3. Map the aggregate as a document: `@MongoEntity(collection = "...")`, the natural ID as
   `@BsonId`, no column annotations. Keep the ID a business identifier.
4. Make the repository a `PanacheMongoRepositoryBase`. Nothing else attributes the phase-two
   outbox and the delivery log to MongoDB, and nothing else binds a MongoDB transaction to the
   platform's - both come with this one line.
5. Configure `quarkus.mongodb.database` and leave the connection string out while developing:
   the dev services then start a MongoDB and run it as a **replica set**, which is what MongoDB
   transactions need. Naming an address switches the dev services off, so a test which does that
   has to bring a replica set of its own.
6. Read the startup line naming the transaction each aggregate is processed in. It is the
   fastest check that step 4 worked, and in production it is where you see that the deployment
   is a standalone MongoDB - VanillaBP warns about that, because every transactional write fails
   there.
7. Use the `camunda8` profile or another remote BPMS. An embedded engine needs a relational
   database, so it does not fit an application without one; combining them means the engine and
   the aggregate commit separately.
8. Copy `LoanApprovalIT` including `aFailedStartLeavesNothingBehind`. A test which only walks
   the happy path does not show what this blueprint is about.

## Verifying

```bash
bin/camunda8_cluster.sh start   # in the monorepo, or bring your own cluster
mvn install verify
```

`camunda8` is the only profile of this blueprint and it is active by default. Docker is
required: the dev services start MongoDB as a container, and the cluster runs in containers as
well.

Both tests of `LoanApprovalIT` have to pass. `theServiceTaskFillsTheAggregate` proves the wiring
between BPMN and code, `aFailedStartLeavesNothingBehind` proves the aggregate and the outbox
entry share one transaction - if the second one fails while the first passes, the MongoDB
transaction is not in place: check the repository and whether the deployment is a replica set.
`ApplicationSmokeTest` passing means the application boots with the module on the classpath.

Do not report success without having run this.
