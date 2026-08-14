# bpmn-async-task

Adds a task the workflow waits at while a surrounding system takes its time: the handler
sends the request and returns, and the task is completed or canceled later by its id. A
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

|           Name           |                                                 Where it occurs                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------------------|
| `requestPartnerApproval` | the `@WorkflowTask` method, the Camunda 7 `camunda:delegateExpression` and the Camunda 8 `zeebe:taskDefinition` |
| `partner-rejected`       | the constant `Workflow.PARTNER_REJECTED` and the `errorCode` of `bpmn:error` in the model                       |
| `informCustomer`         | the `@WorkflowTask` method behind the waiting task and the task definition of that service task                 |
| `noteRejection`          | the `@WorkflowTask` method on the error path and the task definition of that service task                       |

The error code is the contract between code and model: if the two drift apart, the canceled
task is not caught by the boundary event and the workflow ends as an incident.

## Core files

|                                            File                                            |                                                             Why it matters                                                             |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the waiting task, an error boundary event referencing `bpmn:error` with `errorCode="partner-rejected"`, and a service task per way out |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method of that task: aggregate, `@TaskId`, `@TaskEvent`. Returning does NOT complete the task                      |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `completeTask` and `cancelTask`, plus the BPMN error code as a constant                                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | sends the request once, keeps the task id, answers the task when the surrounding system replies                                        |
| `loan-approval/src/main/java/.../loanapproval/PartnerApprovalClient.java`                  | the port to the surrounding system, so a test can put a simulator in its place                                                         |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the callback the answer arrives at, carrying the task id                                                                               |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `partnerApprovalTaskId`, plus the attributes the service tasks behind the waiting task write                                           |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per way the task ends: still open, completed, canceled                                                                        |

## Boilerplate files

|                                      File                                      |                                             Purpose                                              |
|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                     | the BPMS profiles and the VanillaBP BOM import                                                   |
| `loan-approval/pom.xml`                                                        | `vanillabp-spring-boot-support`, never an adapter                                                |
| `application/pom.xml`                                                          | the BPMS adapter, the only place a BPMS is named                                                 |
| `application/src/main/java/.../Application.java`                               | the Spring Boot application, in the parent package of the module                                 |
| `application/src/main/resources/application.yaml`                              | the datasource, and the optional import of the file below                                        |
| `application/src/main/camunda7/resources/camunda7-webapps.yaml`                | the demo user of Camunda's web applications; on the classpath in the Camunda 7 profile only      |
| `loan-approval/src/main/java/.../loanapproval/LocalPartnerApprovalClient.java` | stand-in for the surrounding system so the blueprint runs alone; replace it with the real client |
| `loan-approval/src/test/java/.../TestApplication.java`                         | the minimal application the module's test boots                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                      | base class of the integration test: waits for workflow progress                                  |
| `loan-approval/src/test/java/.../Simulator.java`                               | base class of a stand-in for a surrounding system                                                |
| `application/src/test/java/.../ApplicationSmokeTest.java`                      | boots the application, which validates the BPMN-to-code wiring                                   |
| `docs/loan_approval.png`                                                       | the picture of the process the README shows, rendered from the BPMN model                        |

`TestApplication`, `WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are
identical in every blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Add the task to the BPMN and model it so that it can stay open: on Camunda 7 that is
   `camunda:delegateExpression`, not `camunda:expression`; on Camunda 8 a plain
   `zeebe:taskDefinition`. If the surrounding system can end the business case in a way the
   process has to react to, attach an error boundary event and declare a `bpmn:error` whose
   `errorCode` is the string the code will pass.
2. Add an attribute for the task id to the workflow aggregate. Without it the task cannot
   be answered later, because its id is the only handle to it.
3. Add an interface for the surrounding system, and the real client as its implementation.
   Do not call the system from `WorkflowTaskHandler`.
4. Add the `@WorkflowTask` method named after the task definition. It takes the aggregate,
   `@TaskId` for the id and `@TaskEvent` to tell delivery from cancellation, and it calls
   `Service` for each of the two. Never complete the task there.
5. Add the business methods to `Service`: one that returns early if the request went out
   already, sends it and stores the id; one that drops the id when the task was canceled;
   and one per answer the API accepts. Annotate the API-facing ones with `@Transactional`,
   never the ones the task handler calls.
6. Have the answering method reject a task id which is not the one stored on the aggregate.
   An answer arrives from outside and outlives the task it belongs to.
7. Add `completeTask` and `cancelTask` calls to `Workflow`, one method per business event,
   and keep the BPMN error code there as a constant.
8. Add the callback endpoint carrying the task id, and log the URLs continuing the process
   when the request goes out.
9. Copy `LoanApprovalIT` and write one test per way the task ends.

If the answer of the surrounding system arrives as a BPMN message rather than as a call
into your application, this is the wrong pattern - use message correlation instead
(`bpmn-message-correlation`).

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass:

- the surrounding system was asked once, the aggregate carries the task id, and the service
  task behind the waiting one has NOT run, which is what proves the task stays open,
- after `completeTask` that service task has run,
- after `cancelTask` the service task on the error path has run and the stored id is gone.

If the model wires the task in a way that does not allow it to stay open, the application
does not start at all: on Camunda 7, `camunda:expression` next to a handler declaring
`@TaskId` aborts the boot with a message naming the task and `camunda:delegateExpression` as
the fix. If the workflow ends in an incident instead of taking the error path, the error code
in the code and the one in the model differ.

Do not report success without having run this.
