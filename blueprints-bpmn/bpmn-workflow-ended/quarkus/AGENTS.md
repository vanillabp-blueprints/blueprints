# bpmn-workflow-ended

Tells the application that a workflow ended, whichever end event it reached, without a service
task in front of each of them. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

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
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per end. The one without a task in front of it is the proof          |

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

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean.

## Adding this blueprint to an existing project

1. Decide what the application needs at the end. **Work** belongs in a task in front of the end
   event, where the model shows it. **The fact that the case is closed** belongs in a
   `@WorkflowEnded` method.
2. Add ONE method annotated with `@WorkflowEnded` to the class already annotated with
   `@WorkflowService`. It may take the workflow aggregate, a `WorkflowEnd`, or both in either
   order.
3. Write the outcome onto the workflow aggregate. That makes "closed" a fact of the business
   case, not something to ask a BPMS (business process management system) about later.
4. **Make it cope with arriving twice.** The notification arrives at least once. So setting a
   timestamp and a status is fine, and sending anything is not.
5. Do NOT let a business decision depend on `endEventId()`, or on the difference between
   `COMPLETED` and `CANCELED`. Camunda 7 names the end event, Camunda 8 does not. Some BPMS
   report a cancelled workflow, others do not report it at all. Record what arrives, and decide
   on the aggregate.
6. Do not model a task in front of an end event just to be told about the end. This blueprint
   replaces that pattern. It would have to be repeated for every end event added later.
7. Extend `LoanApprovalIT` with a test for an end which has NOTHING in front of it. If a test's
   branch ends with a task, it cannot tell whether the notification arrived or the task wrote
   it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass: both ends report, and the branch without a
task in front of its end event reports as well. Do not assert `endEventId` unless the BPMS in
use documents that it reports one. On Camunda 8 it is `null`. The SPI documents that answer for
an engine which cannot tell.

Do not report success without having run this.
