# bpmn-signals

Adds a BPMN signal the application broadcasts: it reaches every workflow of the module
waiting for that name, carries no data and is not buffered. A delta on top of
`module-single`.

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

|          Name           |                                     Where it occurs                                     |
|-------------------------|-----------------------------------------------------------------------------------------|
| `InterestRatePublished` | the constant `Workflow.INTEREST_RATE_PUBLISHED` and the `bpmn:signal` name in the model |
| `applyInterestRate`     | the `@WorkflowTask` method behind the signal event and the task definition of that task |
| `rate_watch`            | the BPMN process id of the second process and its model file                            |
| `ratewatch`             | the Java package of the second use case, `rate-watch` in its REST path                  |

The signal name is the contract between code and model. If the two drift apart, the
broadcast reaches no waiting event and nothing happens - no exception, because a signal
nobody catches is a legitimate outcome.

## Core files

|                                            File                                            |                                              Why it matters                                               |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the signal catch event and the `bpmn:signal` it references                                                |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `sendSignal(signalName)` and the signal name as a constant. No aggregate is passed                        |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | stores the data the signal cannot carry BEFORE broadcasting, and reads it again when a workflow continues |
| `loan-approval/src/main/java/.../loanapproval/model/InterestRate.java`                     | the data a waiting workflow reads: application data, belonging to no workflow                             |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | where each workflow puts what it read                                                                     |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the endpoint triggering the broadcast - deliberately without a business case id in its path               |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | waiting, one broadcast continuing two workflows, and a signal nobody caught                               |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/rate_watch.bpmn`    | a second process of the SAME workflow module, waiting for the same signal                                 |
| `loan-approval/src/main/java/.../ratewatch/`                                               | the second use case: own aggregate, own API, no code shared with the loan approval                        |
| `loan-approval/src/test/java/.../RateWatchIT.java`                                         | proves the scope: one use case broadcasts, the process of the other one continues                         |

## Boilerplate files

|                           File                            |                                       Purpose                                        |
|-----------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                   | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                     | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`         | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`       | the database of the module's own test, and where that test reads its BPMN from       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java` | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java` | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                  | the picture of the process the README shows, rendered from the BPMN model            |

## Adding this blueprint to an existing project

1. Add the signal catch event to the BPMN and declare a `bpmn:signal` for it. There is
   nothing else to model: a signal is a broadcast, so no correlation, no key and no
   expression appears anywhere.
2. Decide where the data belongs that the waiting workflows will need. **A signal transports
   nothing**, not even by way of an aggregate - the sender has none. Ordinary application
   data is the answer, read by the task behind the catch event.
3. Add a method to `Service` which stores that data and only then calls `Workflow`. Annotate
   it with `@Transactional`: on a remote BPMS the broadcast is sent after the commit, so the
   data is in place before any workflow reads it.
4. Add the `sendSignal` call to `Workflow` and keep the signal name there as a constant. The
   method takes no workflow aggregate, and giving it one would be a design error rather than
   a missing parameter.
5. Add the endpoint triggering the broadcast. Give it no business case id: nothing about a
   signal is addressed, and a path suggesting otherwise misleads whoever reads the API.
6. Log the URL that continues the process when a workflow starts waiting, and say in the log
   that it continues every waiting workflow at once.
7. Copy `LoanApprovalIT`: one test for the waiting workflow, one for a single broadcast
   continuing two of them, one for a signal nobody caught.
8. If more than one process of the module is supposed to react, model the same signal in
   each of them and send nothing extra. A broadcast covers the whole workflow module, so
   `rate_watch` here needs no sender of its own.

A second use case in one workflow module brings name clashes, and this blueprint shows
where: the entity gets a name of its own (`@Entity(name = "RateWatch")`), because Hibernate
keeps entity names in one namespace. The beans do not need one. The reference structure
calls a class `Service`, `Workflow`, `ApiController` and `WorkflowTaskHandler` in every use
case, and a bean is injected by its type here, so two classes of the same simple name in
two packages stay apart by themselves.

Sending the same signal in several workflow MODULES is a different matter: it means calling
`sendSignal` on the `ProcessService` of each module. A broadcast is scoped to one workflow
module, and which modules are meant is a business decision VanillaBP does not take.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass:

- before the broadcast the service task behind the catch event has NOT run, which is what
  proves the workflow waits,
- one broadcast continues two workflows nobody addressed, and both read the published value,
- a workflow reaching the catch event after a broadcast stays there until the next one.

`RateWatchIT` proves the scope and has to pass as well: the second process of the module
continues although the broadcast was sent by the other use case.

**A test must broadcast repeatedly rather than once.** A signal is not buffered, and the
workflow aggregate showing progress does not prove the subscription of the catch event
exists yet - on a remote engine those are two transactions. Sending in a poll loop until the
workflows reacted is the honest way to wait for it, and a repeat costs nothing because a
signal nobody waits for reaches nobody.

Do not report success without having run this.
