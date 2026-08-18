# bpmn-error-escalation

A BPMN error raised by business code and an escalation raised by the model, in one process.
A delta on top of `module-single`.

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

`documents-missing` is the error code: the constant `Service.DOCUMENTS_MISSING` and the
`errorCode` of the `bpmn:error`. `large-amount` is the escalation code, and it appears in
the MODEL ONLY - no Java names it, because VanillaBP has no API for raising an escalation.
Business code can end a task with an error; that something is reported upwards while work
carries on is decided by the model.

`verifyDocuments`, `rateRisk`, `signContract`, `requestDocuments` and `informSupervisor` are
task definitions: the name of the `@WorkflowTask` method, the
Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8 job type. Rename it in all
places or in none.

## Core files

|                                            File                                            |                                                                Why it matters                                                                 |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                                | one line, the workflow module ID. Without it the JAR is not a workflow module                                                                 |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the process. One directory per adapter ID, because BPMN carries engine specific attributes                                                    |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task. Contains no business logic, calls `Service` |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | what the application tells the process, e.g. `ProcessService#startWorkflow`. The ONLY class using `ProcessService`                            |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the business code. Calls `Workflow` naming the business event, is called by `WorkflowTaskHandler`, never touches VanillaBP                    |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs                                  |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the module's own configuration, loaded by its file name and taking precedence over `application.yaml`                                         |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | starts a real workflow and waits for the effect of the task                                                                                   |

## Boilerplate files

|                               File                                |                                           Purpose                                           |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                                              |
| `loan-approval/pom.xml`                                           | `vanillabp-spring-boot-support`, never an adapter                                           |
| `application/pom.xml`                                             | the BPMS adapter, the only place a BPMS is named                                            |
| `application/src/main/java/.../Application.java`                  | the Spring Boot application, in the parent package of the module                            |
| `application/src/main/resources/application.yaml`                 | the datasource, and the optional import of the file below                                   |
| `application/src/main/camunda7/resources/camunda7-webapps.yaml`   | the demo user of Camunda's web applications; on the classpath in the Camunda 7 profile only |
| `loan-approval/src/test/java/.../TestApplication.java`            | the minimal application the module's test boots                                             |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                              |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                             |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                         |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model                   |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged. Everything specific to the use case belongs into the test
extending `WorkflowModuleTest`, never into the base class.

## Adding this blueprint to an existing project

1. If the project has no workflow module yet, add a Maven module named after the use case
   and create `src/main/resources/META-INF/workflow-module` containing its ID. If a workflow
   module already exists, use it and skip to step 3.
2. Add `io.vanillabp:vanillabp-spring-boot-support` to that module and one BPMS adapter to
   the application module. Import `io.vanillabp:vanillabp-bom` in the parent POM and omit
   the version of every VanillaBP dependency. The BPMS adapters are the exception: they
   are released independently of the platform, so each of them names its own version.
3. Put the BPMN file into `src/main/resources/<workflow-module-id>/processes/<adapter-id>/`.
   The adapter ID is the configured one, which defaults to the adapter type.
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

For an error, throw `TaskException(errorCode)` from the business method the handler calls,
and attach an error boundary event whose `bpmn:error` carries that code. For an escalation,
model it: a throw event where the process wants to report, and a boundary event - usually
non-interrupting - where it wants to react. Do not look for an API to raise an escalation;
there is none, and a task in a subprocess is wired exactly like one outside it.

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
