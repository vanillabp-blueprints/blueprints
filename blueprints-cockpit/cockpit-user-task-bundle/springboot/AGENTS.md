# cockpit-user-task-bundle

A library which packs the workflow module `loan-approval` and the JAR of its user interface into
one dependency, for an application which runs several workflow modules. It has no code: the
module wires itself. A delta on top of `cockpit-user-task-application`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

The workflow module and the API of the forms are in `cockpit-user-task-backend-springboot`. The
forms are in `cockpit-user-task-frontend-react`, built with npm. Change those two there, release
them, and then raise their versions here. The application which takes bundles is the one
`module-packaging-springboot` shows.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                     Meaning                                      |
|----------------------------|----------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow module                                              |
| `loanapproval`             | use case identifier, Java package                                                |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, REST path, folder of the UI |
| `loan_approval`            | BPMN process ID                                                                  |

Names this blueprint adds:

|                Name                 |                           Where it occurs                            |
|-------------------------------------|----------------------------------------------------------------------|
| `loan-approval-bundle`              | the artifact id applications depend on                               |
| `blueprint.application`             | the package of the test application, which does not reach the module |
| `cockpit-user-task-backend.version` | `pom.xml`: the version of the workflow module                        |
| `cockpit-user-task-ui.version`      | `pom.xml`: the version of the UI JAR                                 |

## Core files

|                      File                       |                                                 Why it matters                                                  |
|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `pom.xml`                                       | the workflow module and the UI JAR as dependencies. The BPMS adapter and the cockpit adapter in test scope only |
| `src/test/java/.../UserInterfaceSmokeTest.java` | asks for `/loan-approval/bc-manifest.json` and for the form of an open task                                     |

## Boilerplate files

|                      File                      |                                         Purpose                                          |
|------------------------------------------------|------------------------------------------------------------------------------------------|
| `src/test/java/.../Application.java`           | the test application, in `blueprint.application`                                         |
| `src/test/java/.../ApplicationSmokeTest.java`  | the context starts and every workflow module is wired                                    |
| `src/test/resources/application.yaml`          | the database and where the reports go, as the application writes them                    |
| `src/test/resources/application-camunda7.yaml` | the Camunda 7 settings of the test, and the same file per other BPMS                     |
| `docs/loan_approval.png`                       | the picture of the process the README shows, rendered from the BPMN model of the backend |

`ApplicationSmokeTest` is identical in every blueprint. Copy it unchanged.

## Adding this blueprint to an existing project

1. Create a Maven project with `jar` packaging. Add the workflow module and the UI JAR of the
   module as dependencies, the UI JAR in `runtime` scope.
2. Do not add a BPMS adapter or a cockpit adapter outside test scope. The application chooses
   them once for all its modules. The rule `no-adapter-in-a-workflow-module` in `pom.xml` breaks
   the build otherwise.
3. Add no code to the bundle. The workflow module has to bring its auto-configuration, as
   `cockpit-user-task-backend` shows. If it does not, add it to the module, not to the bundle.
4. Put a test application into a package which does not reach the module, and copy the two smoke
   tests.

## Verifying

The workflow module and the UI JAR have to be in the local Maven repository first. Then:

```bash
mvn install
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

Both smoke tests have to pass:

- `ApplicationSmokeTest`: the context starts, and `loan-approval` is wired. If the boot ends with
  a missing bean behind a `@WorkflowTask` method, the auto-configuration of the module was not
  loaded.
- `UserInterfaceSmokeTest`: `/loan-approval/bc-manifest.json` lists `UserTaskForm` and
  `UserTaskList`, and the form of an open "Assess risk" task answers with its loan request.

Do not report success without having run this.
