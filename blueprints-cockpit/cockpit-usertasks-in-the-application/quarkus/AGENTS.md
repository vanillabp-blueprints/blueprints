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

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                              Placeholder                              |                                                                                 Meaning                                                                                 |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                                            | base package                                                                                                                                                            |
| `loanapproval`                                                        | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                                       | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                                                       | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                                        | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                                       | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.cockpit-usertasks-in-the-application-quarkus` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `cockpit-usertasks-in-the-application`                                | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|                            Name                             |                                                                   Where it occurs                                                                   |
|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `assessRisk`                                                | the `@WorkflowTask` method, the task definition of the user task in both models, and the name of `AssessRiskTaskData` in `@JsonSubTypes`            |
| `checkDocument`                                             | the `@WorkflowTask` method, the task definition of the multi-instance user task, and the name of `CheckDocumentTaskData` in `@JsonSubTypes`         |
| `UserTask_CheckDocument`                                    | the id of the multi-instance user task in the model and the constant `CHECK_DOCUMENT` of `WorkflowTaskHandler`, which `@MultiInstanceElement` names |
| `documentIds`                                               | the attribute of the aggregate, the collection the model loops over, and `declared-aggregate-values` in `loan-approval.yaml`                        |
| `riskAccepted`                                              | the getter `isRiskAccepted()` of the aggregate and the condition of the gateway in the model                                                        |
| `LOAN_APPROVAL_ASSESS_RISK`, `LOAN_APPROVAL_CHECK_DOCUMENT` | the constants of `Permission`, used by `@RolesAllowed` and by the demo users of the application                                                     |

The names in `@JsonSubTypes` are written into the database with every task. Never rename one
after tasks were stored, or they can no longer be read.

## Core files

|                                            File                                            |                                                                                                                       Why it matters                                                                                                                       |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `tasks`, a lazily loaded map from the task id to a `Task`; `getTask(taskId)` returns an `Optional`; only results of completed tasks; `@DynamicUpdate` because two branches write to it                                                                     |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/Task.java`                    | one user task: `createdAt`, `updatedAt`, `completedAt`, `completedBy`, `data`; `data` is loaded lazily; canceled means completed without a user                                                                                                            |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/TaskData.java`                | the interface of the form contents, with `@JsonTypeInfo` and one `@JsonSubTypes` entry per record                                                                                                                                                          |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/AssessRiskTaskData.java`      | the record of one kind of user task; `CheckDocumentTaskData.java` is the other one                                                                                                                                                                         |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/TaskDataConverter.java`       | the JPA converter which stores `TaskData` as JSON                                                                                                                                                                                                          |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `CREATED` adds a task, `CANCELED` marks it; the multi-instance task reads its element only on `CREATED`                                                                                                                                                    |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `@Transactional` on the class; drafts change the task only; completion checks the task is open and of the expected kind, writes the result into the aggregate, completes the task, calls `Workflow`                                                        |
| `loan-approval/src/main/java/.../loanapproval/UserTaskFormController.java`                 | per form GET, PUT and POST, each with `@RolesAllowed`; implements the generated `UserTaskFormsApi`; the user comes from the `SecurityIdentity`; the mappers of `business-cockpit-quarkus-support` answer an unknown task and a task which is done with 404 |
| `loan-approval/src/main/resources/loan-approval/openapi/user-task-forms.yaml`              | the API as an OpenAPI file. The build generates the interface `UserTaskFormsApi` from it, and `UserTaskFormController` implements it                                                                                                                       |
| `loan-approval/src/main/java/.../loanapproval/config/Permission.java`                      | one authority per kind of user task                                                                                                                                                                                                                        |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | a parallel split, the user task "Assess risk", the multi-instance user task "Check document", a gateway on `riskAccepted`, a terminate end event                                                                                                           |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | the tasks as rows, a draft which stays in its task, completion, cancellation, a task sent twice, tasks and data loaded only when read                                                                                                                      |
| `loan-approval/src/test/java/.../StoredLoanApprovals.java`                                 | reads a loan approval for a test inside a transaction, with all tasks and their data                                                                                                                                                                       |
| `loan-approval/src/test/java/.../UserTaskFormSecurityIT.java`                              | the roles: right one, wrong one, nobody, and a task id of the wrong kind                                                                                                                                                                                   |

## Boilerplate files

|                                File                                 |                                                                                                              Purpose                                                                                                              |
|---------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the Quarkus BOM and the VanillaBP BOM import, the rule which keeps adapters out of the workflow module, and the engine versions the JSON dataformat needs                                                                         |
| `loan-approval/pom.xml`                                             | `vanillabp-quarkus-support`, `business-cockpit-quarkus-support` and `quarkus-security`, never an adapter; the OpenAPI generator; the index of the module's classes; per BPMS profile the adapter and the SPIN plugin for the test |
| `application/pom.xml`                                               | the BPMS adapter and the JSON dataformat of its engine, the only place a BPMS is named                                                                                                                                            |
| `application/src/main/resources/application-camunda7.yaml`          | the Camunda 7 settings of the application, the JSON format and the SPIN plugin included, and the same file per other BPMS                                                                                                         |
| `application/src/main/resources/application.yaml`                   | the datasource, the profile the Maven build filters in, and how users log in: HTTP basic authentication for `/loan-approval/api/*` and two demo users; replace them with the login of your application                            |
| `application/src/main/resources/application-camunda7.yaml`          | the Camunda 7 settings: the name-clash-avoidance mode, the JSON format of the shared list and the demo user of its web applications                                                                                               |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the documents to check, `declared-aggregate-values` for the list the model loops over, and `concurrent-branches-reviewed`, because the branches were checked                                                                      |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration tests: waits for workflow progress                                                                                                                                                                  |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                                                                                                                                                                    |
| `docs/loan_approval.png`                                            | the picture of the process the README shows, rendered from the BPMN model                                                                                                                                                         |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean.

## Adding this blueprint to an existing project

1. Copy `Task`, `TaskData` and `TaskDataConverter` into the model of the workflow module. Give
   the task table a name of your module. Quarkus rewrites the entity classes while it builds the
   application, so `data` is loaded only when read. Add
   `io.vanillabp.businesscockpit:business-cockpit-quarkus-support` to the module.
2. Add `tasks` to the workflow aggregate, a `@OneToMany` map keyed by `taskId` with
   `CascadeType.ALL`, and `getTask(taskId)` returning an `Optional`. Load it lazily, leave it
   out of `equals` and `hashCode`, and read it only inside a transaction of `Service`. If two
   branches of the process write to the aggregate, add `@DynamicUpdate`. When no two branches
   write the same column, say so with
   `vanillabp.workflow-modules.<workflow-module-id>.workflows.<bpmn-process-id>.concurrent-branches-reviewed: true`.
   Otherwise VanillaBP warns at every start that the aggregate has no version attribute.
3. Write one record implementing `TaskData` per kind of user task, with the fields of its form.
   List it in `@JsonSubTypes` with a name which never changes.
4. In the `@WorkflowTask` method of each user task, take `@TaskId` and `@TaskEvent`. On
   `CREATED` add `Task.opened(taskId, <empty record>)` to the aggregate. On `CANCELED` call
   `cancel()` on the task. A multi-instance value is only read on `CREATED`, because it may be
   empty on `CANCELED`.
   On Camunda 7, Operaton and CIB seven the list the model loops over travels as an object
   variable. Set `serialization-format: application/json` and the SPIN engine plugin in the
   profile file of that engine, and add the SPIN JSON dataformat to its Maven profile, as
   `application-camunda7.yaml` and `application/pom.xml` show. Without them the engine stores
   the list with Java serialization, and the adapter warns about it.
5. Put `@Transactional` on `Service`. Write three methods per kind of user task. Read: find the task by id and
   kind, which loads its data before the transaction ends. Save: check that the task is open,
   call `saveDraft`, change nothing else. Complete: check that the task is open, write the
   result into the aggregate, call `complete(result, user)`, then call `Workflow`, which calls
   `completeUserTask`. Refuse an unknown task with `BcUnknownTaskException` and a task which
   is done with `BcTaskNotOpenException`.
6. Add the roles to `Permission`, and `io.quarkus:quarkus-security` to the module. Quarkus
   checks the security annotations of every bean without a switch.
7. Write GET, PUT and POST for each form into an OpenAPI file, with the classes of the forms.
   Let `openapi-generator-maven-plugin` generate the interface and the classes, with the
   settings of `loan-approval/pom.xml`, and implement the interface in the controller. Put
   `@RolesAllowed(<role>)` on each method. Take the user from the `SecurityIdentity`, never from
   the request body. The mappers of `business-cockpit-quarkus-support` answer the exceptions
   about a task id with 404. Quarkus finds them on its own.
8. Let the application decide how users log in, and give its users the roles of the module.
   The users in `application.yaml` are the demo version of that.
9. Copy `LoanApprovalIT` and `UserTaskFormSecurityIT`, and adapt them to your user tasks.

Never write a draft into the aggregate, and never read a draft from a gateway, a service task
or another form. Only the result of a completed task is the truth.

Never read the tasks or their data after the transaction of `Service` has ended. They are loaded
lazily, and Quarkus keeps no session open after it, so such a read fails. A test reads through
`StoredLoanApprovals`, which loads everything inside a transaction.

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
- a task completed once is refused the second time, and a task id of another kind is unknown,
- loading the aggregate loads no task, and reading the tasks loads the data of none of them.
  Reading the data of one task loads that data only.

`UserTaskFormSecurityIT` proves the protection: the right authority reads, saves and completes,
another one gets `AccessDeniedException`, and nobody logged in gets
`AuthenticationCredentialsNotFoundException`. It calls the controller outside of a transaction,
so it also proves that a form can be shown after the transaction of `Service` has ended.

If a user task never shows up in `tasks`, the wiring between BPMN and code is wrong. The startup
log then names the BPMN task without a method, or the method without a task. If the start fails
because of `documentIds`, the list is missing from `declared-aggregate-values`.

Do not report success without having run this.
