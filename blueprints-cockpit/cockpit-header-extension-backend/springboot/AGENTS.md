# cockpit-header-extension-backend

Adds what the Business Cockpit needs from a workflow module which puts a button into the header
of the cockpit: the OpenAPI of the start form behind the button, the endpoint which implements
it, the bean which says who sees the module in the cockpit, and the cockpit configuration of the
module. A delta on top of `cockpit-workflow-details-backend`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is one of three parts. The button and the start form are in
`cockpit-header-extension-frontend-react`, built with npm. The application which runs the workflow
module and serves the button is `cockpit-header-extension-application-springboot`. Change this
part first. The other two take it as a released version.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|        Name         |                                                          Where it occurs                                                           |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `reviewLoanRequest` | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 `zeebe:formDefinition externalReference` of the task |
| `LOAN_OFFICERS`     | `LoanApprovalModuleDetails.LOAN_OFFICERS`, the groups of the users in `dev-shell-simulator.yaml` of the test                       |
| `applicant`         | the field of the OpenAPI, the aggregate, the field of the start form in the frontend                                               |
| `amount`            | the field of the OpenAPI, the aggregate, the field of the start form in the frontend                                               |
| `loanRequestId`     | the id of the aggregate, and the field of the answer of the start form                                                             |
| `/wm/loan-approval` | the server of the OpenAPI: the path the cockpit serves the module at                                                               |
| `/loan-approval`    | the path every endpoint of the module starts with, and the path of `workflow-module-uri`                                           |

The cockpit replaces `/wm/loan-approval` by the path of `workflow-module-uri` when it forwards a
request. Keep the server of the OpenAPI, the path of `workflow-module-uri` and the prefix of the
controllers in line, or the start form reaches nothing.

## Core files

|                                      File                                       |                                                            Why it matters                                                            |
|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/openapi/loan-approval.yaml`                   | the contract with the frontend: one operation, `servers: /wm/loan-approval`. Change it together with `StartFormController`           |
| `loan-approval/src/main/java/.../config/LoanApprovalModuleDetails.java`         | the `WorkflowModuleDetailsProvider` of the module: its id and the group `LOAN_OFFICERS`, the only users who see the module           |
| `loan-approval/src/main/java/.../StartFormController.java`                      | the endpoint of the start form, below `/loan-approval/api/loan-requests`. POST, 400 for an empty applicant or an amount below 1, 201 |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`             | `vanillabp.workflow-modules.loan-approval.cockpit.*`: `workflow-module-uri`, `ui-uri-type: ES_MODULES`, `ui-uri-path`                |
| `loan-approval/src/main/java/.../Service.java`                                  | picks the id of the new loan request and starts the process through `Workflow`                                                       |
| `loan-approval/src/main/java/.../WorkflowTaskHandler.java`                      | the `@WorkflowTask` method which keeps `@TaskId`                                                                                     |
| `loan-approval/src/main/java/.../Workflow.java`                                 | `startWorkflow` and `completeUserTask`                                                                                               |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                           | the start form starts a case which reaches the cockpit, refused values, the groups of the module, every OpenAPI operation mapped     |
| `loan-approval/src/test/java/.../CockpitSimulator.java`                         | starts the dev shell simulator as the cockpit of the test, and reads cases and user tasks from it                                    |
| `loan-approval/src/main/java/.../config/LoanApprovalAutoConfiguration.java`     | the module wires itself: component scan, `@EntityScan` and `@EnableJpaRepositories` of its own package, with fully qualified names   |
| `loan-approval/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | names the auto-configuration                                                                                                         |

## Boilerplate files

|                                          File                                          |                                                                    Purpose                                                                    |
|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                             | the VanillaBP BOM import, the versions of the BPMS adapters and of the cockpit adapters, the rule which keeps every adapter in the test scope |
| `loan-approval/pom.xml`                                                                | `vanillabp-spring-boot-support` and the cockpit `support`, the generator of the two APIs, per BPMS profile both adapters for the test         |
| `loan-approval/src/main/resources/openapi/loan-requests.yaml`                          | the GET endpoints for a human: start, approve, show                                                                                           |
| `loan-approval/src/main/java/.../ApiController.java`                                   | implements them                                                                                                                               |
| `loan-approval/src/main/java/.../model/Aggregate.java`                                 | the loan request and the id of the open task                                                                                                  |
| `loan-approval/src/main/java/.../model/AggregateRepository.java`                       | the repository of the aggregate                                                                                                               |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process with its one user task, and the same file per other BPMS                                                                          |
| `loan-approval/src/test/resources/application.yaml`                                    | the database, a short outbox poll interval, the address of the simulator                                                                      |
| `loan-approval/src/test/resources/application-camunda7.yaml`                           | the Camunda 7 settings of the test, and the same file per other BPMS                                                                          |
| `loan-approval/src/test/resources/dev-shell-simulator.yaml`                            | the users the simulator knows: Jane in `LOAN_OFFICERS`, John outside                                                                          |
| `loan-approval/src/test/java/.../ModuleTestApplication.java`                           | the minimal application the module's test boots. It scans nothing, the auto-configuration wires the module                                    |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                              | base class of the integration test: waits for workflow progress                                                                               |
| `loan-approval/src/test/java/.../LocalBackend.java`                                    | runs the module with `spring-boot:test-run` for the development of the user interface                                                         |
| `docs/loan_approval.png`                                                               | the picture of the process the README shows, rendered from the BPMN model                                                                     |

`WorkflowModuleTest` is identical in every blueprint. Copy it unchanged.

## Adding this blueprint to an existing project

1. Start from `cockpit-user-task-backend` for everything a module with a user interface needs:
   the dependency `business-cockpit-spring-boot-support`, the cockpit adapters in the test
   scope, the keys below `vanillabp.workflow-modules.<workflow-module-id>.cockpit`, and the
   auto-configuration.
2. Put the OpenAPI of the start form into the workflow module, at
   `src/main/resources/openapi/<workflow-module-id>.yaml`, with the server
   `/wm/<workflow-module-id>`. One POST operation which takes what the form asks for and answers
   201 with the id of the new workflow aggregate. Generate the interface with the
   `openapi-generator-maven-plugin`, as `loan-approval/pom.xml` does.
3. Implement the interface in a controller below `/<workflow-module-id>`. Check the values the
   OpenAPI requires and answer 400 before a process starts. Let the service pick the id of the
   aggregate and start the process through `Workflow`. Never ask the cockpit to start a case.
4. If not every user of the cockpit may start the process, add a bean which implements
   `WorkflowModuleDetailsProvider`, with the id of the module and the groups which may see it.
   One bean per module. It decides which modules the cockpit lists for a user, and so who sees
   the button. It does not decide about tasks and cases, and it does not protect the endpoint.
5. Keep the bean inside the package the auto-configuration scans, so the module brings it along.
6. Copy `CockpitSimulator`, `dev-shell-simulator.yaml` and the dependency plugin execution of
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

- the start form endpoint answers 201 with the id of a new loan request, the process waits in its
  user task, and the cockpit lists the new case under that id with its open user task,
- an empty applicant, an amount of 0 and a missing amount get 400, and no loan request is started,
- the module has exactly one `WorkflowModuleDetailsProvider`, for `loan-approval`, with the group
  `LOAN_OFFICERS`,
- an approved loan request ends its case (`endReason` says `COMPLETED`),
- every operation of the OpenAPI of the start form has its endpoint.

If the simulator does not start, the test prints the end of its log. The whole log is in
`loan-approval/target/dev-shell-simulator.log`. If a case never reaches the simulator, look for
a message of the cockpit adapter at startup. It names a missing key.

Do not report success without having run this.
