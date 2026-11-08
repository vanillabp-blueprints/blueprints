# cockpit-user-task-application-proxy

Runs the workflow module `loan-approval` without a UI jar. The micro-frontends come from the nginx
image of the frontend, and the application forwards their paths to it with one route of Spring
Cloud Gateway. A delta on top of `cockpit-user-task-application`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

This is the third of three parts. The workflow module is in
`cockpit-user-task-backend-springboot`. The forms and the nginx image are in
`cockpit-user-task-frontend-react`. Change those first. This part takes them as a released
version.

Pick this blueprint only if the user wants to deploy the backend and the user interface apart,
for example for development and test. For production, propose `cockpit-user-task-application` or
`cockpit-user-task-bundle`, and say why: only a build which takes the UI jar checks that the forms
and the API fit. The README has the reasons.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                        Meaning                                        |
|----------------------------|---------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                          |
| `loanapproval`             | use case identifier, Java package                                                     |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, REST path, final name of the jar |
| `loan_approval`            | BPMN process ID                                                                       |

Blueprint-specific names, each occurring in more than one place:

|                     Name                     |                                      Where it occurs                                       |
|----------------------------------------------|--------------------------------------------------------------------------------------------|
| `user-interface.address`                     | `application.yaml`, `UserInterfaceRoute`, `docker-compose.yaml` and `UserInterfaceProxyIT` |
| `user-interface.image`                       | the property in `pom.xml`, which failsafe hands to `UserInterfaceProxyIT`                  |
| `/loan-approval/**`, `/loan-approval/api/**` | the paths in `UserInterfaceRoute`: the files go to the nginx, the API stays                |
| `vanillabp-blueprints/cockpit-user-task-ui`  | the nginx image of the frontend, in `pom.xml` and `docker-compose.yaml`                    |
| `cockpit-user-task-backend.version`          | the version of the workflow module in `pom.xml`                                            |

## Core files

|                     File                      |                                                                  Why it matters                                                                  |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/java/.../UserInterfaceRoute.java`   | the route: every GET below `/loan-approval/` except `/loan-approval/api/` goes to `user-interface.address`, path unchanged                       |
| `src/main/resources/application.yaml`         | `user-interface.address`, the database, `vanillabp.cockpit.rest.*`                                                                               |
| `pom.xml`                                     | the workflow module and no UI jar, `spring-cloud-starter-gateway-server-webmvc`, the Spring Cloud BOM, the BPMS profiles with both adapters each |
| `src/test/java/.../UserInterfaceProxyIT.java` | starts the nginx image, asks for the manifest and every file it names, and checks that the API stays in the application                          |
| `docker-compose.yaml`                         | the application, the nginx, the Business Cockpit, its MongoDB and the dev shell simulator with its users                                         |

## Boilerplate files

|                      File                      |                                  Purpose                                   |
|------------------------------------------------|----------------------------------------------------------------------------|
| `src/main/java/.../Application.java`           | the `@SpringBootApplication`, in the parent package of the workflow module |
| `src/main/resources/application-camunda7.yaml` | the Camunda 7 settings, and the same file per other BPMS                   |
| `src/test/java/.../ApplicationSmokeTest.java`  | boots the application and checks that every workflow module is wired       |
| `docs/loan_approval.png`                       | the picture of the process the README shows                                |

`ApplicationSmokeTest` is identical in every blueprint. Copy it unchanged.

## Adding this blueprint to an existing project

1. Make sure the frontend builds an nginx image which serves the micro-frontends below
   `/<workflow-module-id>/`, the same path as the UI jar.
2. Remove the UI jar from the dependencies of the application.
3. Add `spring-cloud-starter-gateway-server-webmvc` and import `spring-cloud-dependencies`. Take
   the train the cockpit takes. The comment in `pom.xml` says why it is not newer.
4. Copy `UserInterfaceRoute`. Replace `loan-approval` in both paths by your workflow module id.
   Keep the exception for the API. Without it, the API goes to the nginx as well. With several
   workflow modules, add one route per module, or one predicate per module to the same route.
5. Add `user-interface.address` to `application.yaml`, and set it per stage.
6. Copy `UserInterfaceProxyIT` and the property `user-interface.image` in `pom.xml`. Point the
   property at the image of your frontend. Build that image before the test runs, in CI as well.
7. Copy `docker-compose.yaml` if the user wants everything on one machine. Set
   `workflow-module-uri` there to the name of the application inside the compose network.

## Verifying

```bash
mvn install
```

That needs the workflow module in the local Maven repository and the nginx image in the local
Docker. Build the backend with `mvn install` and the frontend with `npm run build` and
`npm run build:image` first. `UserInterfaceProxyIT` stops with a message which names the image if
it is missing. Do not replace the image with a stand-in. The test exists to prove that the real
image fits.

The integration test proves the aspect and has to pass:

- the manifest comes through the application, with the `Cache-Control: no-cache` of the nginx,
- every file the manifest names comes through the application as JavaScript,
- `/loan-approval/api/...` is answered by the application, not by the nginx.

`-Pcamunda8` needs a running cluster and a configured `vanillabp.adapters.camunda8.rest-address`.
Check both before you report a failure of that profile as a defect of the generated code.

Do not report success without having run this.
