# cockpit-user-task-backend

Adds what the Business Cockpit needs from a workflow module whose user tasks it shows: the
OpenAPI of the forms in the workflow module, the endpoints which implement it, a details
provider, and the cockpit configuration of the module. A delta on top of `bpmn-user-task`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is one of three parts. The forms are in `cockpit-user-task-frontend-react`, built with npm.
The application which runs the workflow module and serves the forms is the blueprint
`cockpit-user-task-application`. Change this part first. The other two take it as a released
version.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                        Placeholder                         |                                                                                 Meaning                                                                                 |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                                 | base package                                                                                                                                                            |
| `loanapproval`                                             | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name, prefix of the module's own keys              |
| `loan_approval`                                            | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                             | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                            | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.cockpit-user-task-backend-quarkus` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `cockpit-user-task-backend`                                | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|        Name         |                                                              Where it occurs                                                              |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `assessRisk`        | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 `zeebe:formDefinition externalReference` of the first task  |
| `checkDocuments`    | the same for the second task                                                                                                              |
| `assess-risk`       | the path of the first form's endpoints, in the OpenAPI                                                                                    |
| `check-documents`   | the path of the second form's endpoints                                                                                                   |
| `riskAssessment`    | the key in the details of a user task (`WorkflowTaskHandler.RISK_ASSESSMENT`), the field of the first form, the column of the frontend    |
| `documentsNote`     | the field of the second form                                                                                                              |
| `/wm/loan-approval` | the server of the OpenAPI: the path the cockpit serves the module at                                                                      |
| `/loan-approval`    | the path every endpoint of the module starts with, the path of `workflow-module-uri` and the start of `@Path` of `UserTaskFormController` |

The cockpit replaces `/wm/loan-approval` by the path of `workflow-module-uri` when it forwards a
request. Keep the server of the OpenAPI, the path of `workflow-module-uri` and the `@Path` of
`UserTaskFormController` in line, or a form reaches nothing.

## Core files

|                                    File                                     |                                                                                                 Why it matters                                                                                                 |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/openapi/loan-approval.yaml` | the contract with the frontend: four operations, `servers: /wm/loan-approval`. Change it together with `UserTaskFormController`                                                                                |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`         | `vanillabp.workflow-modules.loan-approval.cockpit.*`: `workflow-module-uri`, `ui-uri-type: ES_MODULES`, `ui-uri-path`                                                                                          |
| `loan-approval/src/main/java/.../WorkflowTaskHandler.java`                  | one `@WorkflowTask` method per user task, keeping `@TaskId`, and the `@UserTaskDetailsProvider` method filling `details`                                                                                       |
| `loan-approval/src/main/java/.../UserTaskFormController.java`               | the endpoints of the forms, below `/loan-approval/api/loan-requests/{loanRequestId}`, each with the task id. Implements the generated `UserTasksApi`. GET reads, POST completes, 404 if not open, 409 if taken |
| `loan-approval/src/main/java/.../Cockpit.java`                              | asks the cockpit, with the cookie of the request, who the user is and whom the task is assigned to, through the generated clients of the official GUI API, built by `RestClients` from `loan-approval.cockpit` |
| `loan-approval/src/main/resources/loan-approval/openapi/loan-requests.yaml` | the GET endpoints which start and show a loan approval. The build generates `LoanRequestsApi` from it, and `ApiController` implements it                                                                       |
| `loan-approval/src/main/java/.../Service.java`                              | finds the loan request by its id, checks that the task is open on it, writes the input, completes the task through `Workflow`                                                                                  |
| `loan-approval/src/main/java/.../Workflow.java`                             | `startWorkflow` and `completeUserTask`                                                                                                                                                                         |
| `loan-approval/src/main/java/.../model/Aggregate.java`                      | per user task the id of the open task and the input of its form                                                                                                                                                |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                       | both tasks through the endpoints, what the cockpit was told, and every OpenAPI operation mapped                                                                                                                |
| `loan-approval/src/test/java/.../CockpitSimulator.java`                     | starts the dev shell simulator as the cockpit of the test                                                                                                                                                      |
| `loan-approval/src/test/java/.../CockpitGuiApi.java`                        | stands in for the two answers of the official GUI API which `Cockpit` asks for, because the simulator does not know who is logged in                                                                           |

## Boilerplate files

|                             File                             |                                                                                                              Purpose                                                                                                               |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                   | the Quarkus BOM and the VanillaBP BOM import, the versions of the BPMS adapters and of the cockpit adapters, the rule which keeps every adapter in the test scope                                                                  |
| `loan-approval/pom.xml`                                      | `vanillabp-quarkus-support`, `business-cockpit-quarkus-support`, the REST client and the index of the module's classes; per BPMS profile both adapters for the test; the profile `local`, which runs the module with `quarkus:dev` |
| `loan-approval/src/test/resources/application.yaml`          | the database, a free port, a short outbox poll interval, the address of the simulator                                                                                                                                              |
| `loan-approval/src/test/resources/application-camunda7.yaml` | the Camunda 7 settings of the test, and the same file per other BPMS                                                                                                                                                               |
| `loan-approval/src/test/resources/dev-shell-simulator.yaml`  | the users the simulator knows. It does not start without one                                                                                                                                                                       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`    | base class of the integration test: waits for workflow progress                                                                                                                                                                    |
| `docs/loan_approval.png`                                     | the picture of the process the README shows, rendered from the BPMN model                                                                                                                                                          |

`WorkflowModuleTest` is identical in every blueprint. Copy it unchanged. Every test class carries
`@QuarkusTest` itself. Inheriting it from the base class does not make the test a bean.

## Adding this blueprint to an existing project

1. Put the OpenAPI of your forms into the workflow module, at
   `src/main/resources/loan-approval/openapi/<workflow-module-id>.yaml`. Give it the server
   `/wm/<workflow-module-id>`. The frontend reads this file through a git submodule of the
   backend repository. The test of the workflow module reads it from the classpath.
2. Add `io.vanillabp.businesscockpit:business-cockpit-quarkus-support` to the workflow module,
   with the version of the cockpit. Do not import the BOM of the cockpit, because it imports the
   BOM of another platform. Add the cockpit adapter of your BPMS next to the BPMS adapter,
   wherever the BPMS adapter is declared: `businesscockpit-<bpms>-adapter-quarkus`. In the
   workflow module, both adapters stay in the test scope. The rule
   `no-adapter-in-a-workflow-module` of the blueprint POM breaks the build otherwise.
3. Write the keys below `vanillabp.workflow-modules.<workflow-module-id>.cockpit` into the
   module's own configuration file: `workflow-module-uri` ending with `/<workflow-module-id>`,
   `ui-uri-type`, `ui-uri-path`, `i18n-languages`, `bpmn-description-language` and
   `initiator-source`. The application writes `vanillabp.cockpit.rest.base-url`. A missing key
   ends the boot with a message which names it. Follow the message.
4. Add an attribute per user task for the id of the open task, and one per form field. A form
   names the business id and the task id, so `Service` reads the aggregate by its id and checks
   the task id on it.
5. Add a `@WorkflowTask` method per user task to `WorkflowTaskHandler`. It keeps the `@TaskId`
   on the aggregate through `Service`. Do not complete the task there.
6. Add a `@UserTaskDetailsProvider` method to `WorkflowTaskHandler`. Put the values the columns
   of the task list show into `details`. Do not put `null` values into the map.
7. Let `openapi-generator-maven-plugin` generate the interface and the classes from the OpenAPI,
   with the settings of `loan-approval/pom.xml`. Implement the interface in a bean with
   `@Path("/<workflow-module-id>/api/<resource>/{businessId}")`, for example
   `/loan-approval/api/loan-requests/{loanRequestId}`. The paths of the file are relative to the
   server of the cockpit, and Jakarta REST takes the `@Path` of the class instead of the one of
   the interface. Write no other path and no class of a request or an answer by
   hand. GET returns what the form shows, POST writes the input to the aggregate and completes
   the task. Every path names the business id of the case and the task id. Throw
   `BcTaskNotOpenException` with both for a task which is not open on that case, which answers 404.
   Write the input to the aggregate only on completion. Before POST completes a task, ask
   `Cockpit` whether somebody else took it over, and throw `BcTaskClaimedByAnotherUserException`
   if so, which answers 409. The mappers of `business-cockpit-quarkus-support` give both answers
   on their own. Copy `Cockpit` for that, set `KEYS` to
   `<workflow-module-id>.cockpit`, add `official-gui-api-quarkus-client` and
   `quarkus-rest-client-jackson` to the module. `Cockpit` builds the clients only if the
   application sets `<workflow-module-id>.cockpit.base-url`, and asks nothing without it. Write
   no call of the cockpit by hand. Keep `WithoutTheCockpitAddressIT`, which starts the module
   without the key.
   The GET endpoints for a person with a browser (`loan-requests.yaml`) follow the same pattern:
   the server of the file is `http://localhost:8080/<workflow-module-id>`, its paths start with
   `/api`, and `ApiController` carries `@Path("/<workflow-module-id>/api/loan-requests")`. The
   URLs stay the server plus the path.
8. Copy `CockpitSimulator`, `CockpitGuiApi`, `dev-shell-simulator.yaml` and the dependency plugin
   execution of `loan-approval/pom.xml` which copies the simulator. Adapt `LoanApprovalIT`,
   keeping the tests which check every operation of both OpenAPI files against the endpoints.
   The test calls the endpoints through the client which the execution `user-task-forms-test-client` of
   `loan-approval/pom.xml` generates from the OpenAPI file, and writes no path and no field by
   hand.

The module needs no class which wires it. Quarkus finds its beans, its entity and its repository
through the Jandex index of the JAR, which the `jandex-maven-plugin` of `loan-approval/pom.xml`
writes. The files of the user interface come from the UI jar: Quarkus serves the files below
`META-INF/resources/` of every JAR.

## Verifying

```bash
mvn install
```

That runs on Camunda 7, which is embedded and needs no infrastructure. The test starts the dev
shell simulator itself, on a free port. `-Pcamunda8` needs a running cluster and a configured
`vanillabp.adapters.camunda8.rest-address`. Check both before you report a failure of that
profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- the cockpit gets both user tasks, under the ids the handlers got, with `ES_MODULES`,
- the endpoints read the forms and complete the tasks, and the workflow ends,
- the report of the second task carries `riskAssessment` in its details,
- a task which is not open gets 404, an empty input gets 400,
- a task somebody else took over in the cockpit gets 409 and stays open,
- every operation of the OpenAPI has its endpoint.

If the simulator does not start, the test prints the end of its log. The whole log is in
`loan-approval/target/dev-shell-simulator.log`. If a task never reaches the simulator, look for
a message of the cockpit adapter at startup. It names a missing key.

Do not report success without having run this.
