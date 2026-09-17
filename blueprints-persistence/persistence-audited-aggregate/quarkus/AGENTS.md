# persistence-audited-aggregate

Keeps every state the workflow aggregate ever had, with the person who caused it, using
Hibernate Envers. On top of that it shows the seam VanillaBP offers for it: an outbox entry
which reports may ask to be served the aggregate as it was when the entry was planned. A
delta on top of `module-single`.

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

|               Name                |                                       Where it occurs                                        |
|-----------------------------------|----------------------------------------------------------------------------------------------|
| `assessRisk`                      | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 form reference |
| `payOutLoan`                      | the `@WorkflowTask` method of the task after the decision and its task definition            |
| `loan-approval:COMPLIANCE_NOTICE` | the name of the outbox operation, persisted with every entry, namespaced                     |
| `LOAN_APPROVAL_REVISION`          | the table of the revision entity, referenced in the README                                   |

**The rule this blueprint is built on:** the id naming a state has to exist before the report
about that state is written down, and that happens before the transaction is flushed.
Everything else here follows from that one sentence.

## Core files

|                                            File                                            |                                                Why it matters                                                |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `@Audited`, which is the whole of switching the auditing on                                                  |
| `loan-approval/src/main/java/.../loanapproval/audit/AuditedChange.java`                    | the revision entity: the number, the moment and who made the change                                          |
| `loan-approval/src/main/java/.../loanapproval/audit/ChangeBeingMade.java`                  | the id naming the change and the person making it, and how long each of them lives                           |
| `loan-approval/src/main/java/.../loanapproval/audit/AuditedAggregatePersistence.java`      | `getAuditingId` and `loadByIdAndAuditingId`: the two methods VanillaBP asks, and the early revision          |
| `loan-approval/src/main/java/.../loanapproval/audit/ComplianceNotices.java`                | the outbox operation of the application, planned in the transaction of the decision and asking for its state |
| `loan-approval/src/main/java/.../loanapproval/ComplianceArchive.java`                      | the port to the archive; `LocalComplianceArchive` is the stand-in to replace                                 |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | three tasks, so the case has a state before and after the decision                                           |
| `loan-approval/src/test/java/.../ComplianceArchiveSimulator.java`                          | the archive which is away at the first attempt, which is what makes the delay reproducible                   |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | asserts what the archive was told and what the trail says                                                    |

## Boilerplate files

|                               File                                |                                        Purpose                                        |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                                        |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and `quarkus-hibernate-envers`, never an adapter          |
| `application/pom.xml`                                             | the BPMS adapter, the only place a BPMS is named                                      |
| `application/src/main/resources/application.yaml`                 | the datasource, and how long the outbox waits before it tries again                   |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                        |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                       |
| `loan-approval/src/test/java/.../Simulator.java`                  | base class of a stand-in for a surrounding system                                     |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process, plus the trail and the name of the person acting |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model             |

`WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged. Everything specific to the use case belongs into
the test extending `WorkflowModuleTest`, never into the base class.

## Adding this blueprint to an existing project

1. Decide whether the case needs an auditing at all. It costs a second write per change and
   a table which only grows, and it pays where somebody has to answer later for what was
   decided. Do not switch it on because it is available.
2. Add `quarkus-hibernate-envers` and put `@Audited` on the workflow aggregate. That alone
   gives the audit table and a revision table called `REVINFO`, and the schema tool creates
   both.
3. Add a revision entity and its listener. The trail names a person that way, which is
   usually the reason for the auditing, and the same listener writes the id which lets a
   state be named before it exists. Neither can be injected, so both travel on the thread.
   The person is bound around the call which opens the transaction, because Envers writes
   the revision when that transaction commits and a name taken back earlier comes too late.
4. Implement `AggregatePersistenceAware` for the aggregate. Everything but the two auditing
   methods is the repository spelled out. `getAuditingId` answers the id of the change the
   running transaction is making, `loadByIdAndAuditingId` looks up the revision carrying that
   id and reads the aggregate at it, answering `null` where it is gone. Both have defaults
   which behave like an application without an auditing, so an application which implements
   neither keeps working.
5. Name the state with an id of your own, not with the revision number. Envers numbers a
   revision while the transaction commits, which is after a report about the event was
   written down. Do not reach for `AuditReader#getCurrentRevision(..., true)` either: it is
   deprecated and what it points at runs at commit time as well. The version attribute of an
   application which uses optimistic locking is the other candidate, and it is assigned per
   write, so a transaction which writes twice names a state its audit row does not carry.
6. Let a report say which state it is about. VanillaBP carries the id with it and hands the
   aggregate of that state to the delivery. Everything written back into the BPMS reads the
   state of the moment it is written instead, because that is where the case goes on.
7. Handle the state which is gone. An auditing is cleaned up at some point and an entry may
   wait longer, so the load answers nothing and the report has to fall back to the current
   state and say so in the log.

What this does not cover: MongoDB, where nothing of this is built in and an application
versions its documents itself, and everything which does not come from the aggregate. The
assignee of a user task, its candidates and its due date are read out of the BPMS while the
entry is dispatched, and no auditing of yours reaches them.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` has to pass. Its first test decides the risk assessment, waits until the
payout was booked and then asserts that the notice the archive received carries the decision
without the payout, which is the aspect of this blueprint. Its second test asserts that the
trail names four changes and the author of each. `ApplicationSmokeTest` passing means the
application boots with the module on the classpath.

The test depends on the outbox trying again quickly: `vanillabp.outbox.attempt-frequency` is
two seconds in `src/test/resources/application.yaml`, against half a minute by default. A
test which waits for a notice without setting it runs into its timeout.

Do not report success without having run this.
