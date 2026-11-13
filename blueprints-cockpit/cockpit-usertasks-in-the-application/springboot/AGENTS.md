# cockpit-usertasks-in-the-application

Keeps every user task as a row of its own next to the workflow aggregate, with the content of
its form as `TaskData`. A draft stays in the task. The result reaches the aggregate when the
task is completed, in the transaction which completes it in the BPMS. The endpoints of each form
are protected by an authority of the module. A delta on top of `bpmn-user-task`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

Use this blueprint instead of `bpmn-user-task` when a user task can be open more than once at
the same time, or when its form saves drafts. `bpmn-user-task` writes the answer straight into
the aggregate, which is only right when neither is the case.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|                            Name                             |                                                                   Where it occurs                                                                   |
|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `assessRisk`                                                | the `@WorkflowTask` method, the task definition of the user task in both models, and the name of `AssessRiskTaskData` in `@JsonSubTypes`            |
| `checkDocument`                                             | the `@WorkflowTask` method, the task definition of the multi-instance user task, and the name of `CheckDocumentTaskData` in `@JsonSubTypes`         |
| `UserTask_CheckDocument`                                    | the id of the multi-instance user task in the model and the constant `CHECK_DOCUMENT` of `WorkflowTaskHandler`, which `@MultiInstanceElement` names |
| `documentIds`                                               | the attribute of the aggregate, the collection the model loops over, and `declared-aggregate-values` in `loan-approval.yaml`                        |
| `riskAccepted`                                              | the getter `isRiskAccepted()` of the aggregate and the condition of the gateway in the model                                                        |
| `LOAN_APPROVAL_ASSESS_RISK`, `LOAN_APPROVAL_CHECK_DOCUMENT` | the constants of `Permission`, used by `@Secured` and by the demo users of the application                                                          |

The names in `@JsonSubTypes` are written into the database with every task. Never rename one
after tasks were stored, or they can no longer be read.

## Core files

|                                            File                                            |                                                                              Why it matters                                                                              |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `tasks`, a map from the task id to a `Task`; `getTask(taskId)` returns an `Optional`; only results of completed tasks; `@DynamicUpdate` because two branches write to it |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/Task.java`                    | one user task: `createdAt`, `updatedAt`, `completedAt`, `completedBy`, `data`; canceled means completed without a user                                                   |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/TaskData.java`                | the interface of the form contents, with `@JsonTypeInfo` and one `@JsonSubTypes` entry per record                                                                        |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/AssessRiskTaskData.java`      | the record of one kind of user task; `CheckDocumentTaskData.java` is the other one                                                                                       |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/TaskDataConverter.java`       | the JPA converter which stores `TaskData` as JSON                                                                                                                        |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `CREATED` adds a task, `CANCELED` marks it; the multi-instance task reads its element only on `CREATED`                                                                  |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | drafts change the task only; completion checks the task is open and of the expected kind, writes the result into the aggregate, completes the task, calls `Workflow`     |
| `loan-approval/src/main/java/.../loanapproval/UserTaskFormController.java`                 | per form GET, PUT and POST, each with `@Secured`; the user comes from the `Principal`                                                                                    |
| `loan-approval/src/main/java/.../loanapproval/config/Permission.java`                      | one authority per kind of user task                                                                                                                                      |
| `loan-approval/src/main/java/.../loanapproval/config/MethodSecurityConfiguration.java`     | `@EnableMethodSecurity(securedEnabled = true)`, in the module                                                                                                            |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | a parallel split, the user task "Assess risk", the multi-instance user task "Check document", a gateway on `riskAccepted`, a terminate end event                         |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | the tasks as rows, a draft which stays in its task, completion, cancellation, a task sent twice                                                                          |
| `loan-approval/src/test/java/.../UserTaskFormSecurityIT.java`                              | the authorities: right one, wrong one, nobody, and a task id of the wrong kind                                                                                           |

## Boilerplate files

|                                File                                 |                                                       Purpose                                                        |
|---------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the VanillaBP BOM import and the rule which keeps adapters out of the workflow module                                |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support` and `spring-boot-starter-security`, never an adapter; the BPMS profiles for the test |
| `application/pom.xml`                                               | the BPMS adapter, the only place a BPMS is named                                                                     |
| `application/src/main/java/.../Application.java`                    | the Spring Boot application, in the parent package of the module                                                     |
| `application/src/main/java/.../SecurityConfiguration.java`          | how users log in: HTTP Basic for `/api/**`, two demo users; replace it with the login of your application            |
| `application/src/main/resources/application.yaml`                   | the datasource, and the profile the Maven build filters in                                                           |
| `application/src/main/resources/application-camunda7.yaml`          | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications                      |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the documents to check, and `declared-aggregate-values` for the list the model loops over                            |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                                                                      |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration tests: waits for workflow progress                                                     |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                                                       |
| `docs/loan_approval.png`                                            | the picture of the process the README shows, rendered from the BPMN model                                            |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged.

## Adding this blueprint to an existing project

1. Copy `Task`, `TaskData` and `TaskDataConverter` into the model of the workflow module. Give
   the task table a name of your module.
2. Add `tasks` to the workflow aggregate, a `@OneToMany` map keyed by `taskId` with
   `CascadeType.ALL`, and `getTask(taskId)` returning an `Optional`. Load it eagerly, or read it
   only inside a transaction. If two branches of the process write to the aggregate, add
   `@DynamicUpdate`.
3. Write one record implementing `TaskData` per kind of user task, with the fields of its form.
   List it in `@JsonSubTypes` with a name which never changes.
4. In the `@WorkflowTask` method of each user task, take `@TaskId` and `@TaskEvent`. On
   `CREATED` add `Task.opened(taskId, <empty record>)` to the aggregate. On `CANCELED` call
   `cancel()` on the task. A multi-instance value is only read on `CREATED`, because it may be
   empty on `CANCELED`.
5. In `Service`, write three methods per kind of user task, each `@Transactional`. Read: find
   the task by id and kind. Save: check that the task is open, call `saveDraft`, change nothing
   else. Complete: check that the task is open, write the result into the aggregate, call
   `complete(result, user)`, then call `Workflow`, which calls `completeUserTask`.
6. Add the authorities to `Permission`, and `@EnableMethodSecurity(securedEnabled = true)` to a
   configuration class of the module.
7. Add GET, PUT and POST for each form to the controller, each with `@Secured(<authority>)`.
   Take the user from the `Principal`, never from the request body.
8. Let the application decide how users log in, and give its users the authorities of the
   module. `SecurityConfiguration` is the demo version of that.
9. Copy `LoanApprovalIT` and `UserTaskFormSecurityIT`, and adapt them to your user tasks.

Never write a draft into the aggregate, and never read a draft from a gateway, a service task
or another form. Only the result of a completed task is the truth.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- after the start, the aggregate holds one task for the risk assessment and one per document,
  all open,
- after a draft is saved, the task holds it and the aggregate does not,
- after completion, the result is in the aggregate and the task says who completed it,
- after a too high risk, the open document checks are canceled, and their drafts never reached
  the aggregate,
- a task completed once is refused the second time, and a task id of another kind is unknown.

`UserTaskFormSecurityIT` proves the protection: the right authority reads, saves and completes,
another one gets `AccessDeniedException`, and nobody logged in gets
`AuthenticationCredentialsNotFoundException`.

If a user task never shows up in `tasks`, the wiring between BPMN and code is wrong. The startup
log then names the BPMN task without a method, or the method without a task. If the start fails
because of `documentIds`, the list is missing from `declared-aggregate-values`.

Do not report success without having run this.
