# bpmn-user-task

Adds a user task. The workflow waits for a person. The application keeps the task's id, and
later completes or cancels the task with it. A delta on top of `module-single`.

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

|       Name       |                                                    Where it occurs                                                     |
|------------------|------------------------------------------------------------------------------------------------------------------------|
| `assessRisk`     | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 `zeebe:formDefinition externalReference` |
| `loan-withdrawn` | the constant `Workflow.LOAN_WITHDRAWN` and the `errorCode` of `bpmn:error` in the model                                |
| `informCustomer` | the `@WorkflowTask` method behind the user task and the task definition of that service task                           |
| `noteWithdrawal` | the `@WorkflowTask` method on the error path and the task definition of that service task                              |

Which BPMN attribute of a user task carries the task definition depends on the BPMS (business
process management system). So the two models name it differently, and both mean `assessRisk`.
The error code is the contract between code and model. If the two drift apart, the boundary
event does not catch the canceled task, and the workflow ends as an incident.

## Core files

|                                            File                                            |                                                                         Why it matters                                                                         |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the user task carrying the task definition, an error boundary event referencing `bpmn:error` with `errorCode="loan-withdrawn"`, and a service task per way out |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method of the user task: aggregate, `@TaskId`, `@TaskEvent`. Returning does NOT complete the task                                          |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `completeUserTask` and `cancelUserTask`, plus the BPMN error code as a constant                                                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | keeps the task id when the task opens, drops it when the task is gone, answers it when the API calls                                                           |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the GET endpoints answering and withdrawing, both carrying the task id                                                                                         |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `riskAssessmentTaskId`, plus the attributes the service tasks behind the user task write                                                                       |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per way the task ends: still open, answered, canceled                                                                                                 |

## Boilerplate files

|                           File                            |                                       Purpose                                        |
|-----------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                   | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                     | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`         | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`       | the database of the module's own test                                                |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java` | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java` | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                  | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean.

## Adding this blueprint to an existing project

1. Add the user task to the BPMN, and give it the task definition the BPMS reads it from
   (`camunda:formKey` for Camunda 7, `zeebe:formDefinition externalReference` together with
   `zeebe:userTask` for Camunda 8, see the adapter's wiki). If the application has to be able
   to cancel the task, attach an error boundary event. Declare a `bpmn:error` whose `errorCode`
   is the string the code will pass.
2. Add an attribute for the task id to the workflow aggregate. Without it the task cannot
   be answered later, because its id is the only handle to it.
3. Add a `@WorkflowTask` method named after the task definition to `WorkflowTaskHandler`. It
   takes the aggregate, `@TaskId` for the id, and `@TaskEvent` to tell creation from
   cancellation. It calls `Service` for each of the two. Do not complete the task there, and
   never throw `TaskException` in it. When the task is created, there is nothing to complete by
   BPMN error.
4. Add the business methods to `Service`. The first stores the id and does whatever notifies
   the people who have to act. The second drops the id when the task was canceled. Then add one
   per answer the API accepts. Annotate the ones the API calls with `@Transactional`, never the
   ones the task handler calls.
5. Let the answering methods reject a task id which is not the one stored on the aggregate. A
   task id reaches the application as a link somebody kept. So it can live longer than the
   task.
6. Add `completeUserTask` and `cancelUserTask` calls to `Workflow`, one method per business
   event, and keep the BPMN error code there as a constant.
7. Add GET endpoints for the answers, with the task id in the path. When the task is created,
   log the URLs which continue the process.
8. Copy `LoanApprovalIT` and write one test per way the task ends.

A user task without a handler is allowed. The check of the wiring does not ask for one, because
such a task is meant to be worked on through a form or a task list. Add the handler when the
application has to know about the task. That is the case as soon as it wants to complete it
itself.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- the aggregate holds the task id, and the service task behind the user task has not run. So
  the workflow really waits,
- after `completeUserTask` the service task behind the user task has run,
- after `cancelUserTask` the service task on the error path has run, and the stored id is gone.
  That shows the cancellation notification arrived.

The third one only runs where the BPMS supports canceling a user task. The root POM hands the
BPMS of the build to the tests as the system property `blueprint.bpms`, and the test is
annotated for it. A build which reports it as skipped is not a defect.

If a task never runs, the wiring between BPMN and code is wrong. The startup log then names the
BPMN task without a method, or the method without a task. If the workflow ends in an incident
instead of taking the error path, the error code in the code differs from the one in the model.

Do not report success without having run this.
