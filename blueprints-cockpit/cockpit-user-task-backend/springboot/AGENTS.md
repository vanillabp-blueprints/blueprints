# cockpit-user-task-backend

Adds what the Business Cockpit needs from a workflow module whose user tasks it shows: the
OpenAPI of the forms in the workflow module, the endpoints which implement it, a details
provider, and the cockpit configuration of the module. A delta on top of `bpmn-user-task`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is one of three parts. The forms are in `cockpit-user-task-frontend-react`, built with npm.
The application which runs the workflow module and serves the forms is
`cockpit-user-task-application-springboot`. Change this part first. The other two take it as a
released version.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|        Name         |                                                             Where it occurs                                                              |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `assessRisk`        | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 `zeebe:formDefinition externalReference` of the first task |
| `checkDocuments`    | the same for the second task                                                                                                             |
| `assess-risk`       | the path of the first form's endpoints, in the OpenAPI and in `UserTaskFormController`                                                   |
| `check-documents`   | the path of the second form's endpoints                                                                                                  |
| `riskAssessment`    | the key in the details of a user task (`WorkflowTaskHandler.RISK_ASSESSMENT`), the field of the first form, the column of the frontend   |
| `documentsNote`     | the field of the second form                                                                                                             |
| `/wm/loan-approval` | the server of the OpenAPI: the path the cockpit serves the module at                                                                     |
| `/loan-approval`    | the path every endpoint of the module starts with, and the path of `workflow-module-uri`                                                 |

The cockpit replaces `/wm/loan-approval` by the path of `workflow-module-uri` when it forwards a
request. Keep the server of the OpenAPI, the path of `workflow-module-uri` and the prefix of the
controllers in line, or a form reaches nothing.

## Core files

|                                       File                                        |                                                               Why it matters                                                               |
|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/openapi/loan-approval.yaml`                     | the contract with the frontend: four operations, `servers: /wm/loan-approval`. Change it together with `UserTaskFormController`            |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`               | `vanillabp.workflow-modules.loan-approval.cockpit.*`: `workflow-module-uri`, `ui-uri-type: ES_MODULES`, `ui-uri-path`                      |
| `loan-approval/src/main/java/.../WorkflowTaskHandler.java`                        | one `@WorkflowTask` method per user task, keeping `@TaskId`, and the `@UserTaskDetailsProvider` method filling `details`                   |
| `loan-approval/src/main/java/.../UserTaskFormController.java`                     | the endpoints of the forms, below `/loan-approval/api/user-tasks/{userTaskId}`. GET reads, POST completes, 404 if not open                 |
| `loan-approval/src/main/java/.../Service.java`                                    | finds the loan request by the id of its open task, writes the input, completes the task through `Workflow`                                 |
| `loan-approval/src/main/java/.../Workflow.java`                                   | `startWorkflow` and `completeUserTask`                                                                                                     |
| `loan-approval/src/main/java/.../model/Aggregate.java`                            | per user task the id of the open task and the input of its form                                                                            |
| `loan-approval/src/main/java/.../model/AggregateRepository.java`                  | the finders by task id                                                                                                                     |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                             | both tasks through the endpoints, what the cockpit was told, and every OpenAPI operation mapped                                            |
| `loan-approval/src/test/java/.../CockpitSimulator.java`                           | starts the dev shell simulator as the cockpit of the test                                                                                  |
| `loan-approval/src/main/java/.../config/LoanApprovalAutoConfiguration.java`       | the module wires itself: component scan, `@EntityScan` and `@EnableJpaRepositories` of its own package, with fully qualified bean names    |
| `loan-approval/src/main/java/.../config/UserInterfaceRouteAutoConfiguration.java` | the route `/loan-approval/**` without `/loan-approval/api/**` to `loan-approval.user-interface.address`, only with the gateway and the key |
| `loan-approval/src/main/resources/META-INF/spring/...AutoConfiguration.imports`   | names both auto-configurations                                                                                                             |

## Boilerplate files

|                             File                             |                                                                    Purpose                                                                    |
|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                   | the VanillaBP BOM import, the versions of the BPMS adapters and of the cockpit adapters, the rule which keeps every adapter in the test scope |
| `loan-approval/pom.xml`                                      | `vanillabp-spring-boot-support` and `business-cockpit-spring-boot-support`; per BPMS profile both adapters for the test                       |
| `loan-approval/src/test/resources/application.yaml`          | the database, a short outbox poll interval, the address of the simulator                                                                      |
| `loan-approval/src/test/resources/application-camunda7.yaml` | the Camunda 7 settings of the test, and the same file per other BPMS                                                                          |
| `loan-approval/src/test/resources/dev-shell-simulator.yaml`  | the users the simulator knows. It does not start without one                                                                                  |
| `loan-approval/src/test/java/.../ModuleTestApplication.java` | the minimal application the module's test boots. It scans nothing, the auto-configuration wires the module                                    |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`    | base class of the integration test: waits for workflow progress                                                                               |
| `loan-approval/src/test/java/.../LocalBackend.java`          | runs the module with `spring-boot:test-run` for the development of the forms                                                                  |
| `docs/loan_approval.png`                                     | the picture of the process the README shows, rendered from the BPMN model                                                                     |

`WorkflowModuleTest` is identical in every blueprint. Copy it unchanged. Do not take the
`TestApplication` of other blueprints. Its component scan would register every bean of the
module a second time, under another name.

## Adding this blueprint to an existing project

1. Put the OpenAPI of your forms into the workflow module, at
   `src/main/resources/openapi/<workflow-module-id>.yaml`. Give it the server
   `/wm/<workflow-module-id>`. The frontend reads this file through a git submodule of the
   backend repository. The test of the workflow module reads it from the classpath.
2. Add `io.vanillabp.businesscockpit:business-cockpit-spring-boot-support` to the workflow
   module. Add the cockpit adapter of your BPMS next to the BPMS adapter, wherever the BPMS
   adapter is declared: `businesscockpit-<bpms>-adapter-spring-boot`. In the workflow module,
   both adapters stay in the test scope. The rule `no-adapter-in-a-workflow-module` of the
   blueprint POM breaks the build otherwise.
3. Write the keys below `vanillabp.workflow-modules.<workflow-module-id>.cockpit` into the
   module's own configuration file: `workflow-module-uri` ending with `/<workflow-module-id>`,
   `ui-uri-type`, `ui-uri-path`, `i18n-languages`, `bpmn-description-language` and
   `initiator-source`. The application writes `vanillabp.cockpit.rest.base-url`. A missing key
   ends the boot with a message which names it. Follow the message.
4. Add an attribute per user task for the id of the open task, and one per form field. Add a
   finder by task id to the repository.
5. Add a `@WorkflowTask` method per user task to `WorkflowTaskHandler`. It keeps the `@TaskId`
   on the aggregate through `Service`. Do not complete the task there.
6. Add a `@UserTaskDetailsProvider` method to `WorkflowTaskHandler`. Put the values the columns
   of the task list show into `details`. Do not put `null` values into the map.
7. Add the endpoints of the OpenAPI to a controller below `/<workflow-module-id>`. GET returns
   what the form shows, POST writes the input to the aggregate and completes the task. Answer
   404 for a task id which is not open. Write the input to the aggregate only on completion.
8. Copy `LoanApprovalAutoConfiguration` and `UserInterfaceRouteAutoConfiguration` into the
   sub-package `config` of your module, and the imports file. Set `WORKFLOW_MODULE_ID` to your
   module id. The scans of `LoanApprovalAutoConfiguration` name the package of the module by its
   `Service`, so they still find every class of the module. Add
   `spring-cloud-gateway-server-webmvc` to the module as an optional dependency, with the
   Spring Cloud BOM. The route class is the same for every module. The library
   `business-cockpit-spring-boot-support` does not have it yet.
9. Copy `CockpitSimulator`, `dev-shell-simulator.yaml` and the dependency plugin execution of
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

- the cockpit gets both user tasks, under the ids the handlers got, with `ES_MODULES`,
- the endpoints read the forms and complete the tasks, and the workflow ends,
- the report of the second task carries `riskAssessment` in its details,
- a task which is not open gets 404, an empty input gets 400,
- every operation of the OpenAPI has its endpoint.

If the simulator does not start, the test prints the end of its log. The whole log is in
`loan-approval/target/dev-shell-simulator.log`. If a task never reaches the simulator, look for
a message of the cockpit adapter at startup. It names a missing key.

Do not report success without having run this.
