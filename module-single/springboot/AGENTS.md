# module-single

The base blueprint: an application module plus one workflow module packaged as a JAR, JPA
persistence, one BPMN service task. Every other blueprint is a delta on top of this
structure, so build this first and then apply the deltas.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first — it carries the procedure, the reference structure and the list of things never to
do.

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
not fail the build — it makes VanillaBP report at startup that no BPMN file was found.

That resource directory is not decoration: workflow modules share one classpath, so **all**
resources of a module have to live in the one subdirectory named after its ID, and its
classes in one Java package of their own. Only `META-INF/workflow-module` sits outside.
Adding a resource at the classpath root works until a second module ships a file of the same
name.

`retrieveCreditRating` is the task definition: the name of the `@WorkflowTask` method, the
Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8 job type. Rename it in all
places or in none.

## Core files

|                                            File                                            |                                                                   Why it matters                                                                   |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                                | one line, the workflow module ID. Without it the JAR is not a workflow module                                                                      |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the process. One directory per adapter ID, because BPMN carries engine specific attributes                                                         |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task, `ProcessService#startWorkflow` starts a workflow |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs                                       |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the module's own configuration, loaded by its file name and taking precedence over `application.yaml`                                              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | starts a real workflow and waits for the effect of the task                                                                                        |

## Boilerplate files

|                               File                                |                             Purpose                              |
|-------------------------------------------------------------------|------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                   |
| `loan-approval/pom.xml`                                           | `vanillabp-spring-boot-support`, never an adapter                |
| `application/pom.xml`                                             | the BPMS adapter — the only place a BPMS is named                |
| `application/src/main/java/.../Application.java`                  | the Spring Boot application, in the parent package of the module |
| `application/src/main/resources/application.yaml`                 | datasource only; no `vanillabp.*` property is needed             |
| `loan-approval/src/test/java/.../TestApplication.java`            | the minimal application the module's test boots                  |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring   |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                              |

## Adding this blueprint to an existing project

1. If the project has no workflow module yet, add a Maven module named after the use case
   and create `src/main/resources/META-INF/workflow-module` containing its ID. If a workflow
   module already exists, use it and skip to step 3.
2. Add `io.vanillabp:vanillabp-spring-boot-support` to that module and one BPMS adapter to
   the application module. Import `io.vanillabp:vanillabp-bom` in the parent POM and omit
   the version of every VanillaBP dependency.
3. Put the BPMN file into `src/main/resources/<workflow-module-id>/processes/<adapter-id>/`.
   The adapter ID is the configured one, which defaults to the adapter type.
4. Add the workflow aggregate as a JPA entity with the natural ID as `@Id`, plus a Spring
   Data repository for it. If the project already has an entity for this business case, use
   it — do not add a second one.
5. Add the `@WorkflowService` class with a `@WorkflowTask` method per BPMN task, and a method
   starting the workflow through `ProcessService#startWorkflow`.
6. Add GET endpoints starting the process and showing the aggregate.
7. Copy `LoanApprovalIT` and adapt it to the use case.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` has to pass: it starts a workflow and waits until the service task has
written to the aggregate. If the task is never executed, the wiring between BPMN and code is
wrong — the startup log names which BPMN task has no method or which method has no task.
`ApplicationSmokeTest` passing means the application boots with the module on the classpath.

Do not report success without having run this.
