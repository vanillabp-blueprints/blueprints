# persistence-custom

The application brings its own persistence: the workflow aggregate, the phase-two outbox, the
log of delivered tasks, and the unit of work around all three. No database anywhere, the stores
are maps. A delta on top of `module-single`, which changes nothing but the persistence.

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

Blueprint-specific names, each occurring in more than one place:

|      Name       |                                             Where it occurs                                              |
|-----------------|----------------------------------------------------------------------------------------------------------|
| `UnitOfWork`    | the transaction of the application; injected by `Service` and by all three stores                        |
| `loanRequestId` | the id property, returned by `AggregateStore#getAggregateIdName` because a remote BPMS asks for its name |

**The rule this blueprint is built on:** VanillaBP puts everything it does around one aggregate
into one transaction. Where the platform does not manage the persistence, the application says
what that transaction is, and every store of the application enlists in it. A store which
writes straight into its data structure looks correct until the first rollback.

## Core files

|                                      File                                      |                                                    Why it matters                                                     |
|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/persistence/UnitOfWork.java`     | `TransactionRunner`: opens, commits and rolls back the application's transaction, and offers the hooks the stores use |
| `loan-approval/src/main/java/.../loanapproval/persistence/AggregateStore.java` | `AggregatePersistenceAware`: saves and loads the aggregate. Also answers the id, its name and its type                |
| `loan-approval/src/main/java/.../loanapproval/persistence/PhaseTwoStore.java`  | `PhaseTwoOutbox`: keeps the scheduled second phase of a start and hands it back to VanillaBP after the commit         |
| `loan-approval/src/main/java/.../loanapproval/persistence/DeliveryLog.java`    | `TaskDeliveryLog`: remembers processed deliveries so a repeated one is not run twice                                  |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`            | a plain class. No entity, no column, nothing naming a database - that is the point                                    |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                    | opens the unit of work for what the API calls; the methods a task handler calls open nothing                          |
| `loan-approval/src/test/java/.../loanapproval/LoanApprovalIT.java`             | three tests: the happy path, the counters proving whose transaction ran, and the rollback leaving nothing behind      |

## Boilerplate files

|                                  File                                   |                                         Purpose                                          |
|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the Spring Boot parent, the VanillaBP BOM import and the single BPMS profile             |
| `loan-approval/pom.xml`                                                 | `vanillabp-spring-boot-support` and no persistence dependency at all                     |
| `application/pom.xml`                                                   | `vanillabp-spring-boot-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                       | nothing about persistence, because there is nothing to configure                         |
| `application/src/main/resources/application-camunda8.yaml`              | the cluster address, loaded by the profile of that engine                                |
| `loan-approval/src/test/resources/application-camunda8.yaml`            | the same for the module's own test                                                       |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the module's own configuration, loaded by its file name                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: waits for workflow progress                          |
| `loan-approval/src/test/java/.../TestApplication.java`                  | the minimal application booting the module for its test                                  |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                           |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`            | what the application tells the process; the only class using `ProcessService`            |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | what the process tells the application; contains no business logic                       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`       | GET endpoints operating the process                                                      |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model                |

`WorkflowModuleTest`, `TestApplication` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged. Note that the integration test polls the aggregate through a
function here. The base class has this overload for a store which is not a repository.

## Adding this blueprint to an existing project

1. Build `module-single` first, or apply this to an existing workflow module. Everything except
   the persistence is that blueprint unchanged.
2. Only take this route if the platform really does not serve the persistence. A repository is
   less code and has less to get wrong. `persistence-mongodb` shows that case.
3. Write the unit of work first (`TransactionRunner`). Without it VanillaBP has nothing to wrap
   its work in. On a platform without a transaction manager the application does not even boot.
   Implement `requireNew`, `inCurrent`, `requireTransaction` and `isTransactionActive`
   honestly. The last one is what refuses a workflow start when the caller opened nothing.
   Contribute it as a plain bean for the whole application, or as a `TransactionRunnerAware`
   bean per aggregate when several persistences exist side by side.
4. Write `AggregatePersistenceAware`. The methods you leave unimplemented throw an exception
   with a message which names what VanillaBP wanted to do. So start with `getAggregateClass`,
   `save`, `loadById` and `getAggregateId`. Add `getAggregateIdName` as soon as you use a
   remote BPMS (business process management system). It names the process variable the id is
   stored in. Return copies, not the stored instances.
5. Write `PhaseTwoOutbox` and `TaskDeliveryLog`. A remote BPMS needs both. Without the outbox
   the boot fails and names it. Without the delivery log a repeated delivery runs the handler
   twice. To dispatch an entry, ask `PhaseOperationRegistry` for the operation of its name.
   Never interpret an entry.
6. Enlist every store in the unit of work. Collect what a unit of work writes, apply it in
   `afterCommit`, and throw it away in `afterRollback`. In a real store, use whatever its
   technology offers instead. A delivery record for work which was rolled back is the worst of
   the three. The repetition which would have fixed it is answered from the record.
7. Open the unit of work where the application starts something. Do not use `@Transactional` of
   the platform. It would open a transaction which your store is not part of. The methods a
   task handler calls open nothing, exactly as in every other blueprint.
8. Read the startup line which names the transaction each aggregate is processed in. It shows
   that steps 3 and 4 worked, and it names which of the four resolution steps answered.
9. Use a remote BPMS. An embedded engine needs a relational database and calls handlers inside
   its own transaction. A store of your own cannot join that transaction.

## Verifying

```bash
bin/camunda8_cluster.sh start   # in the monorepo, or bring your own cluster
mvn install verify
```

`camunda8` is the only profile of this blueprint, and it is active by default. Nothing else has
to run, because the stores are in memory.

All three tests of `LoanApprovalIT` have to pass. Looking at the outcome cannot replace
`vanillaBpWorksInsideTheUnitOfWorkOfThisApplication`. It counts the units of work, and a
workflow which works while something else commits looks exactly the same.
`aFailedStartLeavesNothingBehind` fails when a store writes without enlisting. If
`ApplicationSmokeTest` passes, the application boots with the module on the classpath.

Do not report success without having run this.
