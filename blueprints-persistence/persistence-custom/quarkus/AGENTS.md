# persistence-custom

The application brings its own persistence: the workflow aggregate, the phase-two outbox, the
log of delivered tasks and the unit of work bracketing all three. No database anywhere, the
stores are maps. A delta on top of `module-single`, changing nothing but the persistence.

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

Blueprint-specific names, each occurring in more than one place:

|      Name       |                                             Where it occurs                                              |
|-----------------|----------------------------------------------------------------------------------------------------------|
| `UnitOfWork`    | the transaction of the application; injected by `Service` and by all three stores                        |
| `loanRequestId` | the id property, returned by `AggregateStore#getAggregateIdName` because a remote BPMS asks for its name |

**The rule this blueprint is built on:** VanillaBP puts everything it does around one aggregate
into one transaction. Where the platform does not manage the persistence, the application says
what that transaction is and every store of the application enlists in it. A store which writes
straight into its data structure looks correct until the first rollback.

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

|                                  File                                   |                                               Purpose                                                |
|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the Quarkus BOM, the VanillaBP BOM import and the single BPMS profile                                |
| `loan-approval/pom.xml`                                                 | `vanillabp-quarkus-support`, the index of the module's classes, and no persistence dependency at all |
| `application/pom.xml`                                                   | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named                 |
| `application/src/main/resources/application.yaml`                       | nothing about persistence, because there is nothing to configure                                     |
| `application/src/main/resources/application-camunda8.yaml`              | the cluster address, loaded by the profile of that engine                                            |
| `loan-approval/src/test/resources/application-camunda8.yaml`            | the same for the module's own test                                                                   |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the module's own configuration, loaded by its file name                                              |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: waits for workflow progress                                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                                       |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`            | what the application tells the process; the only class using `ProcessService`                        |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | what the process tells the application; contains no business logic                                   |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`       | GET endpoints operating the process                                                                  |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model                            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy them
unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the base class is
not enough to make the test a bean. The integration test polls the aggregate through a function,
which is what the base class takes anyway.

## Adding this blueprint to an existing project

1. Build `module-single` first, or apply this to an existing workflow module. Everything except
   the persistence is that blueprint unchanged.
2. Only take this route if the persistence is really not one the platform serves. A repository is
   less code and less to get wrong; `persistence-mongodb` shows that case.
3. Write the unit of work first (`TransactionRunner`). The platform has a transaction of its own
   here, and that is the trap: it commits while your store commits separately, so VanillaBP has
   to be told to use yours. Implement `requireNew`, `inCurrent`, `requireTransaction` and
   `isTransactionActive` honestly: the last one is what a workflow start is refused by when the
   caller opened nothing. Contribute it as a plain bean for the whole application, or as a
   `TransactionRunnerAware` bean per aggregate when several persistences exist side by side.
4. Write `AggregatePersistenceAware`. The methods left unimplemented throw with a message naming
   what VanillaBP wanted to do, so start with `getAggregateClass`, `save`, `loadById` and
   `getAggregateId` and add `getAggregateIdName` as soon as a remote BPMS is used - it names the
   process variable the id is stored in. Return copies rather than the stored instances.
5. Write `PhaseTwoOutbox` and `TaskDeliveryLog`. Both are required for a remote BPMS: without the
   outbox the boot fails naming it, without the delivery log a repeated delivery runs the handler
   twice. Dispatch an entry by asking `PhaseTwoOperationRegistry` for the operation of its name -
   never interpret an entry.
6. Enlist every store in the unit of work: collect what a unit of work writes, apply it in
   `afterCommit`, throw it away in `afterRollback`. In a real store this is whatever its
   technology offers instead. A delivery record for work which was rolled back is the worst of
   the three, because the repetition which would have fixed it is answered from the record.
7. Open the unit of work where the application starts something: no `@Transactional` of the
   platform, it would open a transaction your store is not part of. The methods a task handler
   calls open nothing, exactly as in every other blueprint.
8. Read the startup line naming the transaction each aggregate is processed in. It is the check
   that steps 3 and 4 worked, and it names which of the four resolution steps answered.
9. Use a remote BPMS. An embedded engine needs a relational database and invokes handlers inside
   its own transaction, which a store of your own cannot join.

## Verifying

```bash
bin/camunda8_cluster.sh start   # in the monorepo, or bring your own cluster
mvn install verify
```

`camunda8` is the only profile of this blueprint and it is active by default. Nothing else has to
run: the stores are in memory.

All three tests of `LoanApprovalIT` have to pass.
`vanillaBpWorksInsideTheUnitOfWorkOfThisApplication` is the one which cannot be replaced by
looking at the outcome: it counts the units of work, and a workflow which works while something
else commits looks exactly the same. `aFailedStartLeavesNothingBehind` fails when a store writes
without enlisting. `ApplicationSmokeTest` passing means the application boots with the module on
the classpath.

Do not report success without having run this.
