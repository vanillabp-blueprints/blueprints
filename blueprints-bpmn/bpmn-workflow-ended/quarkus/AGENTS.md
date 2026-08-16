# bpmn-workflow-ended

Tells the application that a workflow ended, whichever end event it reached, without a
service task in front of every one of them. A delta on top of `module-single`.

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

|                       Name                       |                              Where it occurs                               |
|--------------------------------------------------|----------------------------------------------------------------------------|
| `EndEvent_LoanApproved`, `EndEvent_LoanRejected` | the two end events of the model, and what `WorkflowEnd#endEventId` reports |
| `ratedAcceptable`                                | the getter of the aggregate and the condition of the gateway               |
| `closedAt`, `closedBy`, `closedAtEndEvent`       | what the end notification writes onto the aggregate                        |

## Core files

|                                            File                                            |                                Why it matters                                 |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowEnded` method, in the same class as the `@WorkflowTask` methods |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | closes the business case, in business terms, without naming a BPMN element    |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | when the case was closed, how, and at which end event                         |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | two end events: one with a task in front of it, one with nothing              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per end - the one without a task in front of it is the proof         |

## Boilerplate files

|                                File                                 |                                       Purpose                                        |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                             | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                               | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                   | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`                 | the database of the module's own test                                                |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | GET endpoints operating the process                                                  |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`        | starts the workflow                                                                  |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the threshold the gateway routes on                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                            | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Decide what the application needs at the end. **Work** belongs into a task in front of the
   end event, where the model shows it. **The fact that the case is closed** belongs into a
   `@WorkflowEnded` method.
2. Add ONE method annotated with `@WorkflowEnded` to the class already annotated with
   `@WorkflowService`. It may take the workflow aggregate, a `WorkflowEnd`, or both in either
   order.
3. Write the outcome onto the workflow aggregate. That is what makes "closed" a fact of the
   business case rather than something to ask a BPMS about later.
4. **Make it survive arriving twice.** The notification is at-least-once, so setting a
   timestamp and a status is fine and sending anything is not.
5. Do NOT let a business decision depend on `endEventId()` or on the difference between
   `COMPLETED` and `TERMINATED`. Camunda 7 names the end event, Camunda 8 does not; a
   cancelled workflow is reported by some BPMS and by others not at all. Record what arrives,
   decide on the aggregate.
6. Do not model a task in front of an end event just to be told about the end. That is the
   pattern this blueprint replaces, and it has to be repeated for every end event added
   later.
7. Extend `LoanApprovalIT` with a test for an end which has NOTHING in front of it. A test
   whose branch ends with a task cannot tell whether the notification arrived or the task
   wrote it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass: both ends report, and the branch without a
task in front of its end event reports as well. Do not assert `endEventId` unless the BPMS in
use documents that it reports one - on Camunda 8 it is `null`, which is the SPI's documented
answer of an engine which cannot tell.

Do not report success without having run this.
