# module-standalone

The smallest project VanillaBP runs in: one Maven module, in which the application and the
workflow module are the same artifact. A delta on top of `module-single`. Choose this shape
when it is clear that this deployment will never have a second use case.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

`loan-approval` occurs in more places than any other placeholder, and all of them have to
change together: the Maven module directory, the marker file `META-INF/workflow-module`, the
resource directory `src/main/resources/loan-approval/`, the configuration file
`loan-approval.yaml` and its property prefix, and the REST path. If you miss one, the build
does not fail. Instead VanillaBP reports at startup that it found no BPMN file.

That resource directory has a reason. Workflow modules share one classpath. So **all**
resources of a module have to be in the one subdirectory named after its ID, and its classes in
one Java package of their own. Only `META-INF/workflow-module` is outside. A resource at the
classpath root works until a second module ships a file of the same name.

`retrieveCreditRating` is the task definition. It is the name of the `@WorkflowTask` method,
the Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8 job type. Rename it in all
places or in none.

## Core files

|                              File                              |                                                                Why it matters                                                                |
|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/resources/META-INF/workflow-module`                  | one line, the workflow module ID. Without it the application is not a workflow module, and VanillaBP says so                                 |
| `src/main/resources/processes/<adapter-id>/loan_approval.bpmn` | the process, at the classpath root. One directory per adapter ID, because BPMN carries engine specific attributes                            |
| `src/main/java/.../loanapproval/WorkflowTaskHandler.java`      | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task. No business logic here, it calls `Service` |
| `src/main/java/.../loanapproval/Workflow.java`                 | what the application tells the process, e.g. `ProcessService#startWorkflow`. The ONLY class using `ProcessService`                           |
| `src/main/java/.../loanapproval/Service.java`                  | the business code. Calls `Workflow` naming the business event, is called by `WorkflowTaskHandler`, never touches VanillaBP                   |
| `src/main/java/.../loanapproval/model/Aggregate.java`          | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs, shared with no BPMS            |
| `src/main/resources/loan-approval.yaml`                        | the module's own configuration. It is next to `application.yaml` and wins over it                                                            |
| `src/test/java/.../LoanApprovalIT.java`                        | starts a real workflow and waits for the effect of the task                                                                                  |

## Boilerplate files

|                        File                         |                                      Purpose                                       |
|-----------------------------------------------------|------------------------------------------------------------------------------------|
| `pom.xml`                                           | the only POM: the BPMS profiles, the VanillaBP BOM import and every dependency     |
| `src/main/resources/application.yaml`               | the datasource and the profile choosing the BPMS                                   |
| `src/test/java/.../ApplicationSmokeTest.java`       | boots the application and holds the declared workflow module against the wired one |
| `src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                    |
| `src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                |
| `docs/loan_approval.png`                            | the picture of the process the README shows, rendered from the BPMN model          |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean. Everything specific to the use case belongs in the test which
extends `WorkflowModuleTest`, never in the base class.

## Adding this blueprint to an existing project

1. Create `src/main/resources/META-INF/workflow-module` in the application and write the
   workflow module ID into it. That one file makes the application a workflow module. No
   configuration property can do it instead.
2. Add `io.vanillabp:vanillabp-quarkus-integration` and one BPMS (business process management
   system) adapter to the application. Import `io.vanillabp:vanillabp-bom` and omit the version
   of every VanillaBP dependency. The BPMS adapters are the exception. They are released on
   their own, apart from the platform, so each of them names its own version.
3. Put the BPMN file into `src/main/resources/processes/<adapter-id>/`. The adapter ID is the
   configured one. By default it is the adapter type. A directory named after the module works
   as well. A project with a workflow module of its own has to use that.
4. Build no index. The classes are part of the artifact the build processes. A workflow module
   in a Maven module of its own needs an index only because its classes are not.
5. Add the workflow aggregate as a JPA entity with the natural ID as `@Id`, plus a Spring Data
   repository for it. If the project already has an entity for this business case, use it
   instead of adding a second one. Annotate the class with `@NoSyncWithBPMS`. Annotate every
   attribute the model reads with `@SyncWithBPMS`. As long as the model has no condition, timer
   or multi-instance collection, that is none.
6. Add `WorkflowTaskHandler` with the `@WorkflowService` annotation and one `@WorkflowTask`
   method per BPMN task, each doing nothing but calling `Service`. Never annotate the handler
   or the service methods it calls with `@Transactional`. VanillaBP runs a task in a
   transaction of its own and commits it for a `TaskException`. A transaction declared here
   would roll that back. VanillaBP rejects both cases: the annotation on the handler fails the
   boot, and the one on a bean it calls fails the task. So an annotation left in place shows up
   as an error which names the method. Add `Workflow` with one method per business event the
   process has to learn about. Add `Service` with the business methods. If the project already
   has a business service for this use case, add the methods there instead of creating a second
   one. Never inject `ProcessService` into it, and never merge the two workflow classes.
   `Service` uses `Workflow` and is used by `WorkflowTaskHandler`, so merging them creates a
   circular bean reference.
7. Add GET endpoints starting the process and showing the aggregate.
8. Copy `LoanApprovalIT` and adapt it to the use case.

**Before you choose this shape:** it holds for exactly one use case. A second one in the same
artifact shares a classpath, and nothing keeps the two apart. `module-multi` shows what
collides then. Splitting later means moving files and adding a second POM, so nothing here has
to be paid for in advance. But it is a move, and it is easier before there is production data.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` has to pass. It starts a workflow and waits until the service task has written
to the aggregate. If the task never runs, the wiring between BPMN and code is wrong. The
startup log then names the BPMN task without a method, or the method without a task. If
`ApplicationSmokeTest` passes, the application boots with the module on the classpath.

Do not report success without having run this.
