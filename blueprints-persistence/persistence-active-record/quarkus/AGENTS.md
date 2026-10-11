# persistence-active-record

The workflow aggregates store themselves. One extends `PanacheEntityBase` and lives in a
relational database. The other extends `PanacheMongoEntityBase` and lives in MongoDB. Both are
in the same workflow module. The application has no repository and no persistence code.
VanillaBP recognises the idiom per aggregate while the application is built, so an application
may mix them. A delta on top of `module-single`, which changes nothing but the persistence
path.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |
| `credithistory`            | second use case, Java package (`credit-history` in kebab case, `credit_history` as BPMN process ID)                       |

Blueprint-specific name:

|  Name  |                                 Where it occurs                                  |
|--------|----------------------------------------------------------------------------------|
| `byId` | the finder on the aggregate, called by `Service#get` and by the integration test |

**The rule this blueprint is built on:** an aggregate which is an active record is the only
class on its persistence path. So every read the application does has to bring its own
transaction or request context. There is no repository left to declare one on.

**The second rule:** the idiom is resolved per aggregate. So two aggregates of one workflow
module may live in two databases. VanillaBP puts its own stores, the phase-two outbox and the
log of delivered tasks, into the database of the aggregate they belong to. An application only
says something about that where it brought the persistence itself.

## Core files

|                                            File                                            |                                                         Why it matters                                                         |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | extends `PanacheEntityBase`, natural ID as `@Id`, plus the typed finder `byId`. There is deliberately no repository next to it |
| `loan-approval/src/main/java/.../credithistory/model/Aggregate.java`                       | extends `PanacheMongoEntityBase`, natural ID as `@BsonId`, plus `byId`. The second use case, stored in MongoDB                 |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `get` calls `Aggregate.byId` and carries the `@Transactional` the finder needs; the task path carries none                     |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | reads through `Aggregate::byId` and asserts that a workflow started on a remote engine keeps its aggregate                     |
| `loan-approval/src/test/java/.../CreditHistoryIT.java`                                     | the same two assertions for the aggregate MongoDB stores                                                                       |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the process, one service task. Unchanged from the base blueprint                                                               |

## Boilerplate files

|                                  File                                   |                                                  Purpose                                                   |
|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                                            |
| `loan-approval/pom.xml`                                                 | `vanillabp-quarkus-support`, both Panache flavours and the index of the module's classes, never an adapter |
| `application/pom.xml`                                                   | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named                       |
| `application/src/main/resources/application.yaml`                       | the two databases, and nothing about the workflows or their persistence                                    |
| `loan-approval/src/test/resources/application.yaml`                     | the database of the module's own test                                                                      |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the module's own configuration, loaded by its file name                                                    |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: a fresh transaction per poll                                           |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                                             |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`            | what the application tells the process; the only class using `ProcessService`                              |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | what the process tells the application; contains no business logic                                         |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`       | GET endpoints operating the process                                                                        |
| `docs/loan_approval.png`, `docs/credit_history.png`                     | the pictures of the processes the README shows, rendered from the BPMN models                              |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean. Everything specific to the use case belongs in the test which
extends `WorkflowModuleTest`, never in the base class.

## Adding this blueprint to an existing project

1. Build `module-single` first, or apply this to an existing workflow module. Everything except
   the persistence is that blueprint unchanged.
2. Let the aggregate extend `io.quarkus.hibernate.orm.panache.PanacheEntityBase` and keep the
   natural ID as `@Id`. `PanacheEntity` would add a generated `id`, which a workflow aggregate
   has no use for.
3. Delete the repository of that aggregate, and do not implement `AggregatePersistenceAware`
   either. Nothing configures the persistence. VanillaBP resolves the idiom from the class.
   Mind the order, which applies per aggregate. An `AggregatePersistenceAware` of yours wins
   over everything. A repository for that aggregate wins over the active record. Spring Data
   answers last. So a repository left behind keeps winning without a word. The build log says
   which idiom was chosen for which aggregate. Read that line instead of guessing.
4. Put a finder on the aggregate for every question the application asks. Name it after the
   question (`byId` here), not after the query. The inherited `findByIdOptional` is generic and
   gives the caller a `PanacheEntityBase`, which is not worth reading.
5. Give every method which reads the aggregate a transaction: `@Transactional` on the business
   method the API calls, as `Service#get` shows. Without one, the finder has nothing to read
   from. The failure then happens at runtime, not at compile time.
6. Leave the task path alone. `@WorkflowTask` methods and the business methods they call must
   not declare a transaction. VanillaBP owns the one a task runs in and commits it for a
   `TaskException`. A transaction of yours would roll that back. VanillaBP fails the boot if
   the handler has such an annotation, and fails the task if a method further down has one.
7. The workflow module needs `quarkus-hibernate-orm-panache`. The index of its classes
   (`jandex-maven-plugin`) makes the aggregate visible at build time. Without the index the
   idiom cannot be resolved, and the build ends with a message about it.
8. For an aggregate in MongoDB the same steps hold with `quarkus-mongodb-panache`,
   `io.quarkus.mongodb.panache.PanacheMongoEntityBase`, `@MongoEntity` and `@BsonId`. Both may
   exist side by side in one workflow module, one aggregate each, and neither of them says
   which database it belongs to. VanillaBP assigns its own stores, the phase-two outbox and the
   log of delivered tasks, along with the persistence it resolved. So an application with two
   databases configures nothing for them either.
9. MongoDB writes inside the transaction VanillaBP opens, and that needs a replica set. The dev
   services run one. A production deployment has to be one, and VanillaBP warns while starting
   if it is not. Note also that no transaction spans two databases. With a MongoDB aggregate
   the engine's state and the aggregate commit separately, even on the embedded engine. The
   phase-two outbox holds them together.
10. Copy `LoanApprovalIT` and `CreditHistoryIT`, including
    `theStartedWorkflowKeepsItsAggregate`. Starting a workflow on a remote BPMS (business
    process management system) finishes on a thread of VanillaBP, where no class of the
    application is on the stack. The assertion which matters here is that this works without a
    repository. Only `-Pcamunda8` tests it.

Two branches of one workflow which write the same aggregate are the same problem here as with a
repository.
[`persistence-parallel-branches`](https://github.com/vanillabp-blueprints/persistence-parallel-branches-quarkus)
and
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate)
answer it, not this blueprint.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded, but Docker has to be running: the dev services start
a MongoDB for the second use case. For this blueprint that is not enough. Run it on a remote
engine as well, because there the two-phase start is worth watching.

```bash
mvn install verify -Pcamunda8
```

`-Pcamunda8` needs a running cluster and a configured
`vanillabp.adapters.camunda8.rest-address`. Check both before you report a failure of that
profile as a defect of the generated code.

All four tests of `LoanApprovalIT` and `CreditHistoryIT` have to pass. The build log has to
name an active record as the persistence chosen for each of the two aggregates, the Hibernate
ORM one and the MongoDB one. If it names a repository instead, one is still on the classpath.
If `ApplicationSmokeTest` passes, the application boots with the module on the classpath.

Do not report success without having run this.
