# cockpit-workflow-details-backend

Adds what the Business Cockpit needs from a workflow module whose cases it shows on a page of
their own: the OpenAPI of the case page in the workflow module, the endpoints which implement it,
an interrupting event subprocess which withdraws a loan request, a workflow details provider, and the
cockpit configuration of the module. A delta on top of `cockpit-user-task-backend`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is one of three parts. The case page is in `cockpit-workflow-details-frontend-react`, built
with npm. The application which runs the workflow module and serves the page is
`cockpit-workflow-details-application-springboot`. Change this part first. The other two take it
as a released version.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|         Name          |                                                              Where it occurs                                                               |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `reviewLoanRequest`   | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 `zeebe:formDefinition externalReference` of the user task    |
| `WithdrawLoanRequest` | the name of the message in the BPMN files and `Workflow.WITHDRAW_LOAN_REQUEST`                                                             |
| `loanRequestId`       | the id of the aggregate, the path parameter of the OpenAPI, and the correlation key `=loanRequestId` of the message in the Camunda 8 model |
| `applicant`           | the key in the details of a case (`WorkflowTaskHandler.APPLICANT`), the field of the OpenAPI, the column of the frontend                   |
| `recordWithdrawal`    | the `@WorkflowTask` method, the Camunda 7 `camunda:expression` and the Camunda 8 `zeebe:taskDefinition type` of the service task           |
| `status`              | the key in the details of a case (`WorkflowTaskHandler.STATUS`), the field of the OpenAPI, the column of the frontend                      |
| `/wm/loan-approval`   | the server of the OpenAPI: the path the cockpit serves the module at                                                                       |
| `/loan-approval`      | the path every endpoint of the module starts with, and the path of `workflow-module-uri`                                                   |

The cockpit replaces `/wm/loan-approval` by the path of `workflow-module-uri` when it forwards a
request. Keep the server of the OpenAPI, the path of `workflow-module-uri` and the prefix of the
controllers in line, or the page reaches nothing.

## Core files

|                                          File                                          |                                                                                     Why it matters                                                                                     |
|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/openapi/loan-approval.yaml`                          | the contract with the frontend: two operations, `servers: /wm/loan-approval`. Change it together with `CasePageController`                                                             |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the user task, and the event subprocess with `triggeredByEvent="true"`, an interrupting message start event, the service task `recordWithdrawal` and the message `WithdrawLoanRequest` |
| `loan-approval/src/main/resources/loan-approval/processes/camunda8/loan_approval.bpmn` | the same, plus `zeebe:subscription correlationKey="=loanRequestId"` on the message. The adapter does not add it for a start event                                                      |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                    | `vanillabp.workflow-modules.loan-approval.cockpit.*`: `workflow-module-uri`, `ui-uri-type: ES_MODULES`, `ui-uri-path`                                                                  |
| `loan-approval/src/main/java/.../WorkflowTaskHandler.java`                             | the `@WorkflowTask` method keeping `@TaskId`, the one recording the withdrawal, and the `@WorkflowDetailsProvider` method filling `details` with `applicant` and `status`              |
| `loan-approval/src/main/java/.../CasePageController.java`                              | the endpoints of the page, below `/loan-approval/api/cases/{loanRequestId}`. GET reads, POST withdraws, 404 if unknown, 409 if not in review                                           |
| `loan-approval/src/main/java/.../Service.java`                                         | approves, withdraws through `Workflow`, and sets the status `WITHDRAWN` when the service task runs, so the report of the end carries it                                                |
| `loan-approval/src/main/java/.../Workflow.java`                                        | `startWorkflow`, `completeUserTask` and `correlateMessage`                                                                                                                             |
| `loan-approval/src/main/java/.../model/Aggregate.java`                                 | the loan request, the id of the open task and the `Status`                                                                                                                             |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                  | withdraw and approve through the module, what the cockpit was told, and every OpenAPI operation mapped                                                                                 |
| `loan-approval/src/test/java/.../CockpitSimulator.java`                                | starts the dev shell simulator as the cockpit of the test, and reads cases and user tasks from it                                                                                      |
| `loan-approval/src/main/java/.../config/LoanApprovalAutoConfiguration.java`            | the module wires itself: component scan, `@EntityScan` and `@EnableJpaRepositories` of its own package, with fully qualified bean names                                                |
| `loan-approval/src/main/resources/META-INF/spring/...AutoConfiguration.imports`        | names the auto-configuration                                                                                                                                                           |

## Boilerplate files

|                               File                               |                                                                    Purpose                                                                    |
|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                       | the VanillaBP BOM import, the versions of the BPMS adapters and of the cockpit adapters, the rule which keeps every adapter in the test scope |
| `loan-approval/pom.xml`                                          | `vanillabp-spring-boot-support` and the cockpit `support`; per BPMS profile both adapters for the test                                        |
| `loan-approval/src/main/java/.../ApiController.java`             | GET endpoints for a human: start, approve, show                                                                                               |
| `loan-approval/src/main/java/.../model/AggregateRepository.java` | the repository of the aggregate                                                                                                               |
| `loan-approval/src/test/resources/application.yaml`              | the database, a short outbox poll interval, the address of the simulator                                                                      |
| `loan-approval/src/test/resources/application-camunda7.yaml`     | the Camunda 7 settings of the test, and the same file per other BPMS                                                                          |
| `loan-approval/src/test/resources/dev-shell-simulator.yaml`      | the users the simulator knows. It does not start without one                                                                                  |
| `loan-approval/src/test/java/.../ModuleTestApplication.java`     | the minimal application the module's test boots. It scans nothing, the auto-configuration wires the module                                    |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`        | base class of the integration test: waits for workflow progress                                                                               |
| `loan-approval/src/test/java/.../LocalBackend.java`              | runs the module with `spring-boot:test-run` for the development of the page                                                                   |
| `docs/loan_approval.png`                                         | the picture of the process the README shows, rendered from the BPMN model                                                                     |

`WorkflowModuleTest` is identical in every blueprint. Copy it unchanged.

## Adding this blueprint to an existing project

1. Start from `cockpit-user-task-backend` for everything a module with a user interface needs:
   the cockpit `support` dependency, the cockpit adapters in the test scope, the keys below
   `vanillabp.workflow-modules.<workflow-module-id>.cockpit`, and the auto-configuration.
2. Put the OpenAPI of the case page into the workflow module, at
   `src/main/resources/openapi/<workflow-module-id>.yaml`, with the server
   `/wm/<workflow-module-id>`. Key the paths by the id of the workflow aggregate. The cockpit
   hands the page that id as the `businessId` of the case.
3. Add a status attribute to the aggregate, an enum in business words. Name the negative outcome
   after the business, for example `WITHDRAWN` or `ABORTED`, never `CANCELLED`: the cockpit says
   `CANCELLED` for a process which operations cancelled in the BPMS, and that is a different
   thing.
4. Add an interrupting event subprocess with a message start event, a service task which sets the
   status, and an end event to the process. The process ends the normal way. On Camunda 8, give
   its message a `zeebe:subscription` whose correlation key names the id attribute of the
   aggregate, for example `=loanRequestId`. On Camunda 7 and its forks nothing is needed.
5. In the service method the endpoint calls, correlate the message through `Workflow`. Refuse
   when the status says that the case cannot be withdrawn any more.
6. Add a `@WorkflowDetailsProvider` method to `WorkflowTaskHandler`. Put the values the columns of
   the case list show into `details`, the status included. Do not put `null` values into the
   map. Take the business outcome from the status, not from `endReason` of the case.
7. Add the endpoints of the OpenAPI to a controller below `/<workflow-module-id>`. Answer 404 for
   an unknown id and 409 for a case which cannot be withdrawn any more.
8. Copy `CockpitSimulator`, `dev-shell-simulator.yaml` and the dependency plugin execution of
   `loan-approval/pom.xml` which copies the simulator. Adapt `LoanApprovalIT`, keeping the test
   which checks every OpenAPI operation against the endpoints.

## Verifying

```bash
mvn install
```

That runs on Camunda 7, which is embedded and needs no infrastructure. The test starts the dev
shell simulator itself, on a free port. `-Pcamunda8` needs a running cluster and a configured
`vanillabp.adapters.camunda8.rest-address`. Check both before you report a failure of that
profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- the cockpit gets the case under its business id, with `ES_MODULES` and the applicant in its
  details,
- the cockpit lists the open user task of the case, as the page gets it,
- the withdraw endpoint ends the user task and the case through the event subprocess, the case
  ends normally (`endReason` says `COMPLETED`), and its details say `WITHDRAWN`,
- an approved case ends the regular way, and cannot be withdrawn afterwards (409),
- a loan request can be withdrawn once (409 the second time),
- an unknown case gets 404,
- every operation of the OpenAPI has its endpoint.

If the simulator does not start, the test prints the end of its log. The whole log is in
`loan-approval/target/dev-shell-simulator.log`. If a case never reaches the simulator, look for
a message of the cockpit adapter at startup. It names a missing key.

Do not report success without having run this.
