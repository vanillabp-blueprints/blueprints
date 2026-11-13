# cockpit-organizing-usertasks

Describes each user task to the Business Cockpit so that the right people see it: the
initiator, candidate groups which are the authorities of the forms, an assignee where the case
belongs to one person, excluded candidates for the four-eyes principle, the kind of
notification, the due date and the follow-up date, titles and search texts from templates, and
the admitted users who may still read a task later. A delta on top of
`cockpit-usertasks-in-the-application`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

Use this blueprint when a workflow module reports its user tasks to the Business Cockpit and has
to say more about them than the BPMN file does. The workflow module depends on
`io.vanillabp.businesscockpit:support`, never on a cockpit adapter. The application brings the
cockpit adapter, next to the BPMS adapter.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                                 Meaning                                                                  |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                                             |
| `loanapproval`             | use case identifier, Java package                                                                                                        |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, template directory, REST path, Maven module, configuration file |
| `loan_approval`            | BPMN process ID, and the directory of the templates of this process                                                                      |

Blueprint-specific names, each occurring in more than one place:

|                    Name                     |                                                                       Where it occurs                                                                       |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `callCustomer`, `assessRisk`, `approveLoan` | the `@WorkflowTask` methods, the task definitions in the models, `taskDefinition` of the details providers, the template directories, `@JsonSubTypes` names |
| `AdvisorChanged`                            | the message of the boundary event in the models and the constant `ADVISOR_CHANGED` of `Workflow`                                                            |
| `userTaskAccess`                            | the bean name of `UserTaskAccess`, which `@PreAuthorize` of the controller names                                                                            |
| `LOAN_APPROVAL_*`                           | the constants of `Permission`, used by `@Secured`, `@PreAuthorize`, as candidate groups and by the demo users                                               |
| `call-again.ftl`                            | the template next to `task-title.ftl` of `callCustomer`, and the constant `CALL_AGAIN_TEMPLATE` of `WorkflowTaskHandler`                                    |

The names in `@JsonSubTypes` are written into the database with every task. Never rename one
after tasks were stored, or they can no longer be read.

## Core files

|                                            File                                            |                                                                                   Why it matters                                                                                    |
|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@UserTaskDetailsProvider` per task definition, and the `@WorkflowDetailsProvider`; every value is read from the aggregate; a provider never changes the aggregate              |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `lastActionBy` (the initiator), `advisor` (the assignee), `riskAssessedBy` (the excluded candidate), `requestedAt` and `answerPromisedBy` (the due dates)                           |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/Task.java`                    | `editors`: who worked on the task; the admitted users and the read access come from this one list                                                                                   |
| `loan-approval/src/main/java/.../loanapproval/model/usertask/CallCustomerTaskData.java`    | `callAgainAt`, the follow-up date                                                                                                                                                   |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `@Transactional` on the class; writes the user who acted with every action; refuses the approval by the user who assessed the risk; the change of the advisor                       |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `correlateMessage` for the change of the advisor; `BusinessCockpitService#aggregateChanged(loanRequest, taskId)` for changes the BPMS does not notice                               |
| `loan-approval/src/main/java/.../loanapproval/UserTaskFormController.java`                 | GET with `@PreAuthorize("hasAuthority(...) or @userTaskAccess.mayRead(...)")`; `editable` tells which way let the user in; an editor is written when the form is delivered for work |
| `loan-approval/src/main/java/.../loanapproval/UserTaskAccess.java`                         | reads only the editors of one task                                                                                                                                                  |
| `loan-approval/src/main/java/.../loanapproval/config/Permission.java`                      | one authority per kind of task; the same constant is the candidate group                                                                                                            |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the rules about time; `template-loader-path`; `i18n-languages`, `initiator-source: by-application` and the other keys of the cockpit                                                |
| `loan-approval/src/main/resources/loan-approval/**/*.ftl`                                  | the templates: per task definition in `loan_approval/<task-definition>/`, for the whole module directly in `loan-approval/`, one file per language with `_de` in its name           |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | three user tasks with task definitions; a message boundary event on "Call customer" which leads back into it                                                                        |
| `loan-approval/src/test/java/.../OrganizingUserTasksIT.java`                               | one test per pattern; asserts on what was reported to the cockpit; proves that tasks and their data are loaded only when read                                                       |
| `loan-approval/src/test/java/.../ReportRecorder.java`                                      | keeps every report on its way to the dev shell simulator, because the simulator drops excluded candidates, admitted users and the kind of notification                              |

## Boilerplate files

|                            File                             |                                                                                    Purpose                                                                                     |
|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                  | the VanillaBP BOM import, the cockpit version and the rule which keeps adapters out of the workflow module                                                                     |
| `loan-approval/pom.xml`                                     | `vanillabp-spring-boot-support`, `support` of the cockpit and Freemarker, never an adapter; `hibernate-maven-plugin`, which makes `Task#data` lazy; both adapters for the test |
| `application/pom.xml`                                       | the BPMS adapter and the cockpit adapter of the same BPMS, the only place a BPMS is named                                                                                      |
| `application/src/main/java/.../Application.java`            | the Spring Boot application, in the parent package of the module                                                                                                               |
| `application/src/main/java/.../SecurityConfiguration.java`  | how users log in: HTTP Basic for `/api/**`, six demo users; replace it with the login of your application                                                                      |
| `application/src/main/resources/application.yaml`           | the datasource, `open-in-view: false`, where the reports go, and the profile the Maven build filters in                                                                        |
| `loan-approval/src/main/java/.../loanapproval/support/`     | `UnknownTaskException`, `TaskNotOpenException` and their answers 404 and 409, as in the base blueprint                                                                         |
| `loan-approval/src/test/java/.../StoredLoanApprovals.java`  | reads a loan approval for a test inside a transaction, with all tasks and their data                                                                                           |
| `loan-approval/src/test/java/.../CockpitSimulator.java`     | starts the dev shell simulator of the cockpit for the test                                                                                                                     |
| `loan-approval/src/test/resources/dev-shell-simulator.yaml` | the users of the simulator                                                                                                                                                     |
| `loan-approval/src/test/java/.../TestApplication.java`      | the minimal application the module's test boots                                                                                                                                |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`   | base class of the integration tests: waits for workflow progress                                                                                                               |
| `application/src/test/java/.../ApplicationSmokeTest.java`   | boots the application, which validates the BPMN-to-code wiring                                                                                                                 |
| `docs/loan_approval.png`                                    | the picture of the process the README shows, rendered from the BPMN model                                                                                                      |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged.

## Adding this blueprint to an existing project

Apply `cockpit-usertasks-in-the-application` first. The tasks and their data loaded lazily,
`@Transactional` on `Service` and the package `support` come from there. The package `support`
of the module is not the `support` library of the cockpit.

1. Add `io.vanillabp.businesscockpit:support` and `org.freemarker:freemarker` to the workflow
   module. Add the cockpit adapter of your BPMS to the application, and to the module only in
   the test scope.
2. Write the keys of the cockpit into the configuration file of the module, below
   `vanillabp.workflow-modules.<module-id>.cockpit`: `i18n-languages`,
   `bpmn-description-language` and `initiator-source: by-application`. Write
   `vanillabp.cockpit.template-loader-path: "classpath:"` there as well.
3. Write into the aggregate what the reports need and no BPMS records: the user who acted last,
   the person a task belongs to, the user who did a step the next one must be kept from, and
   the dates the due dates are counted from. Set them in `Service`, from the user who logged
   in.
4. Add one `@UserTaskDetailsProvider(taskDefinition = ...)` per task definition to the class
   with `@WorkflowService`. Set the initiator, the candidate group (the authority of the form),
   the assignee where the aggregate names one, the excluded candidates, the due date, the
   notification delivery and the details. Only read the aggregate. A provider runs inside the
   transaction of VanillaBP, so it may read the tasks and their data.
5. Add a `@WorkflowDetailsProvider` which sets the initiator as well.
6. Put the title templates into `<module-id>/<bpmn-process-id>/<task-definition>/task-title.ftl`,
   one file per language with `_<language>` before `.ftl`. Pass a small record to
   `setTemplateContext`. Put the search text into `<module-id>/task-fulltext-search.ftl`, with
   the data no column shows.
7. Add `editors` to the task, write the user into it when the form is delivered for work, and
   report them with `setAdmittedUsers`. Copy `UserTaskAccess` and name it in `@PreAuthorize` of
   every endpoint which reads a form. Return whether the user may work on the form, and keep
   `@Secured` on every endpoint which changes something.
8. Call `BusinessCockpitService#aggregateChanged(aggregate, taskId)` whenever something the
   provider reads changes and the BPMS does not notice: a follow-up date, a new editor.
9. To give a task to other people, end it with a boundary event which leads back into it.
   Never expect a later report to change the assignee or the candidates.
10. Copy `OrganizingUserTasksIT`, `CockpitSimulator` and `ReportRecorder`, and adapt the test
    to your user tasks.

Never let a details provider change the aggregate, and never keep a second list of readers next
to the editors of a task.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`OrganizingUserTasksIT` proves the aspect and has to pass:

- the initiator of each report is the user who caused it, for a task and for the case,
- "Call customer" has the advisor as assignee, `USER_CONFIG`, the address of the CRM and a
  title from a template in two languages,
- a customer not reached gives the task a follow-up date on the next morning and another title,
- a new advisor gets the call as a new task, and the old task is canceled,
- "Assess risk" has a candidate group, no assignee, `SUPPRESS`, a title from a template, the
  details and a search text with the phone number,
- "Approve loan" excludes the user who assessed the risk, the module refuses that user, and
  another approver completes it,
- a user who opened a form for work is an admitted user and may still read the form without
  the authority, while a stranger is refused,
- loading the aggregate loads no task, and reading a task does not load its data.

If a title arrives as the name of the BPMN task, the template was not found: check the
directory names against the module id, the process id and the task definition, and check
`template-loader-path`. If a report fails because it names no initiator, a provider does not set
one. If a task never shows up in `tasks`, the wiring between BPMN and code is wrong, and the
startup log names it.

Do not report success without having run this.
