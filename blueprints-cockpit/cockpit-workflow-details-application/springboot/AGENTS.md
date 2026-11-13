# cockpit-workflow-details-application

The application which runs the workflow module `loan-approval` and serves its case page to the
Business Cockpit. It takes both as dependencies: the workflow module from the backend, the UI
JAR from the frontend. It has no code apart from the class which starts it.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is the last of three parts. The workflow module and the API of the case page are in
`cockpit-workflow-details-backend-springboot`. The case page is in
`cockpit-workflow-details-frontend-react`, built with npm. Change those two there, release them,
and then raise their versions here. Apart from the names, this part is the same as
`cockpit-user-task-application-springboot`.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                     Meaning                                      |
|----------------------------|----------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow module                                              |
| `blueprint.application`    | package of the application, not above the workflow module                        |
| `loanapproval`             | use case identifier, Java package                                                |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, REST path, folder of the UI |
| `loan_approval`            | BPMN process ID                                                                  |

Blueprint-specific names, each occurring in more than one place:

|                         Name                         |                                          Where it occurs                                           |
|------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `cockpit-workflow-details-backend.version`           | `pom.xml`: the version of the workflow module                                                      |
| `cockpit-workflow-details-ui.version`                | `pom.xml`: the version of the UI JAR                                                               |
| `io.vanillabp.blueprint:cockpit-workflow-details-ui` | the coordinates the frontend publishes its UI JAR under                                            |
| `/loan-approval`                                     | the path of `workflow-module-uri`, of the endpoints and of the UI files, checked by the smoke test |
| `9080`                                               | the port of the cockpit in `docker-compose.yaml` and in `vanillabp.cockpit.rest.base-url`          |
| `loan-approval` (account)                            | `bpms-api.*` in `docker-compose.yaml` and `vanillabp.cockpit.rest.authentication.*`                |

## Core files

|                      File                       |                                                     Why it matters                                                      |
|-------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `pom.xml`                                       | the workflow module and the UI JAR in their own versions, and per BPMS profile the BPMS adapter and the cockpit adapter |
| `src/main/resources/application.yaml`           | `vanillabp.cockpit.rest.*`: where the reports go, with which account                                                    |
| `src/test/java/.../UserInterfaceSmokeTest.java` | asks for `/loan-approval/bc-manifest.json` and for the case page of a running case                                      |

## Boilerplate files

|                      File                      |                                         Purpose                                          |
|------------------------------------------------|------------------------------------------------------------------------------------------|
| `src/main/java/.../Application.java`           | `@SpringBootApplication` in a package which is not above the module                      |
| `src/main/resources/application-camunda7.yaml` | the Camunda 7 settings, and the same file per other BPMS                                 |
| `src/test/java/.../ApplicationSmokeTest.java`  | the context starts and every workflow module is wired                                    |
| `docker-compose.yaml`                          | MongoDB, the user directory and the cockpit, for trying the application                  |
| `docker/dev-shell-simulator.yaml`              | the users of that cockpit                                                                |
| `docs/loan_approval.png`                       | the picture of the process the README shows, rendered from the BPMN model of the backend |

`ApplicationSmokeTest` is identical in every blueprint. Copy it unchanged.

## Adding this blueprint to an existing project

1. Add the workflow module as a dependency, in the version the backend released. Add no
   component scan, entity scan or repository package for it. The module wires itself.
2. Add the UI JAR of the module as a `runtime` dependency, in the version the frontend
   released. Do not serve its files with code. Spring Boot serves `META-INF/resources/` of every
   JAR.
3. Add the cockpit adapter of your BPMS next to the BPMS adapter, in every profile:
   `businesscockpit-<bpms>-adapter-spring-boot`.
4. Write `vanillabp.cockpit.rest.base-url` and the account into `application.yaml`. Take the
   password from the environment in a real application.
5. Check that the path of `workflow-module-uri` in the module's configuration is the address the
   application really runs at. Set it in the application if it differs.
6. Copy `UserInterfaceSmokeTest` and adapt the module id and the endpoint to your module.

## Verifying

The workflow module and the UI JAR have to be in the local Maven repository first. Then:

```bash
mvn install
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

Both smoke tests have to pass:

- `ApplicationSmokeTest`: the context starts, and `loan-approval` is wired,
- `UserInterfaceSmokeTest`: `/loan-approval/bc-manifest.json` lists `WorkflowPage` and
  `WorkflowList`, and the case page of a running case answers with its loan request.

If the manifest is missing, the UI JAR is not on the class path, or its files are not below
`META-INF/resources/loan-approval/`. A missing cockpit is no failure. The reports wait in the
outbox, and the log shows a warning.

Do not report success without having run this.
