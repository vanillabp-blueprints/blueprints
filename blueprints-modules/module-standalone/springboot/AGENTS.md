# module-standalone

The smallest project VanillaBP runs in: one Maven module, in which the application and the
workflow module are the same artifact. A delta on top of `module-single`, and the shape to
choose when it is clear that this deployment will never hold a second use case.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

`loan-approval` occurs in more places than any other placeholder, and every one of them has
to change together: the Maven module directory, the marker file `META-INF/workflow-module`,
the resource directory `src/main/resources/loan-approval/`, the configuration file
`loan-approval.yaml` and its property prefix, and the REST path. A missed occurrence does
not fail the build. It makes VanillaBP report at startup that no BPMN file was found.

That resource directory is not decoration: workflow modules share one classpath, so **all**
resources of a module have to live in the one subdirectory named after its ID, and its
classes in one Java package of their own. Only `META-INF/workflow-module` sits outside.
Adding a resource at the classpath root works until a second module ships a file of the same
name.

`retrieveCreditRating` is the task definition: the name of the `@WorkflowTask` method, the
Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8 job type. Rename it in all
places or in none.

## Core files

|                              File                              |                                                                Why it matters                                                                 |
|----------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/resources/META-INF/workflow-module`                  | one line, the workflow module ID. Without it the application is no workflow module, and VanillaBP says so                                     |
| `src/main/resources/processes/<adapter-id>/loan_approval.bpmn` | the process, at the classpath root. One directory per adapter ID, because BPMN carries engine specific attributes                             |
| `src/main/java/.../loanapproval/WorkflowTaskHandler.java`      | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task. Contains no business logic, calls `Service` |
| `src/main/java/.../loanapproval/Workflow.java`                 | what the application tells the process, e.g. `ProcessService#startWorkflow`. The ONLY class using `ProcessService`                            |
| `src/main/java/.../loanapproval/Service.java`                  | the business code. Calls `Workflow` naming the business event, is called by `WorkflowTaskHandler`, never touches VanillaBP                    |
| `src/main/java/.../loanapproval/model/Aggregate.java`          | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs                                  |
| `src/main/resources/loan-approval.yaml`                        | the module's own configuration, next to `application.yaml` and taking precedence over it                                                      |
| `src/test/java/.../LoanApprovalIT.java`                        | starts a real workflow and waits for the effect of the task                                                                                   |

## Boilerplate files

|                        File                         |                                      Purpose                                       |
|-----------------------------------------------------|------------------------------------------------------------------------------------|
| `pom.xml`                                           | the only POM: the BPMS profiles, the VanillaBP BOM import and every dependency     |
| `src/main/java/.../Application.java`                | the Spring Boot application, which is also the workflow module                     |
| `src/main/resources/application.yaml`               | the datasource and the profile choosing the BPMS                                   |
| `src/main/resources/application-camunda7.yaml`      | the demo user of Camunda's web applications; loaded in the Camunda 7 profile only  |
| `src/test/java/.../ApplicationSmokeTest.java`       | boots the application and holds the declared workflow module against the wired one |
| `src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                    |
| `src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                |
| `docs/loan_approval.png`                            | the picture of the process the README shows, rendered from the BPMN model          |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy them
unchanged. There is no `TestApplication` here: a test boots the application itself, because
it is in the same artifact. Everything specific to the use case belongs into the test
extending `WorkflowModuleTest`, never into the base class.

## Adding this blueprint to an existing project

1. Create `src/main/resources/META-INF/workflow-module` in the application containing the
   workflow module ID. That one file is what makes the application a workflow module; there
   is no configuration property doing it instead.
2. Add `io.vanillabp:vanillabp-spring-boot-support` and one BPMS adapter to the application.
   Import `io.vanillabp:vanillabp-bom` and omit the version of every VanillaBP dependency.
   The BPMS adapters are the exception: they are released independently of the platform, so
   each of them names its own version.
3. Put the BPMN file into `src/main/resources/processes/<adapter-id>/`. The adapter ID is the
   configured one, which defaults to the adapter type. A directory named after the module
   works as well and is what a project with a workflow module of its own has to use.
4. Add the workflow aggregate as a JPA entity with the natural ID as `@Id`, plus a Spring
   Data repository for it. If the project already has an entity for this business case, use
   it instead of adding a second one.
5. Add `WorkflowTaskHandler` with the `@WorkflowService` annotation and one `@WorkflowTask`
   method per BPMN task, each doing nothing but calling `Service`. Never annotate the
   handler or the service methods it calls with `@Transactional`: VanillaBP runs a task in
   a transaction of its own and commits it for a `TaskException`, which a transaction
   declared here would roll back. VanillaBP rejects both cases, the annotation on the
   handler by failing the boot and the one on a bean it calls by failing the task, so an
   annotation left in place shows up as an error naming the method. Add `Workflow` with one
   method per business event the process has to learn about. Add `Service` with the business
   methods. If the project already has a business service for this use case, add the methods
   there instead of creating a second one. Never inject `ProcessService` into it, and never
   merge the two workflow classes: `Service` uses `Workflow` and is used by
   `WorkflowTaskHandler`, so merging them creates a circular bean reference.
6. Add GET endpoints starting the process and showing the aggregate.
7. Copy `LoanApprovalIT` and adapt it to the use case.

**Before choosing this shape:** it holds for exactly one use case. A second one in the same
artifact shares a classpath with nothing keeping the two apart, and what collides then is in
`module-multi`. Splitting later is a move of files plus a second POM, so nothing here has to
be paid for in advance - but it is a move, and it is easier before there is production data.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` has to pass: it starts a workflow and waits until the service task has
written to the aggregate. If the task is never executed, the wiring between BPMN and code is
wrong, and the startup log names which BPMN task has no method or which method has no task.
`ApplicationSmokeTest` passing means the application boots with the module on the classpath.

Do not report success without having run this.
