# module-single

The base blueprint: an application module plus one workflow module packaged as a JAR, JPA
(Jakarta Persistence) for persistence, one BPMN service task. Every other blueprint is a delta
on top of this structure. So build this one first, and then apply the deltas.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

`loan-approval` occurs in more places than any other placeholder, and all of them have to
change together: the Maven module directory, the marker file `META-INF/workflow-module`, the
resource directory `src/main/resources/loan-approval/`, the configuration file
`loan-approval.yaml` and its property prefix, the REST path, and the resources location in
`src/test/resources/application.yaml` of the test. If you miss one, the build does not fail.
Instead VanillaBP reports at startup that it found no BPMN file.

That resource directory has a reason. Workflow modules share one classpath. So **all**
resources of a module have to be in the one subdirectory named after its ID, and its classes in
one Java package of their own. Only `META-INF/workflow-module` is outside. A resource at the
classpath root works until a second module ships a file of the same name.

`retrieveCreditRating` is the task definition. It is the name of the `@WorkflowTask` method,
the Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8 job type. Rename it in all
places or in none.

## Core files

|                                            File                                            |                                                                Why it matters                                                                |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                                | one line, the workflow module ID. Without it the JAR is not a workflow module                                                                |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the process. One directory per adapter ID, because BPMN has attributes specific to the engine                                                |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task. No business logic here, it calls `Service` |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | what the application tells the process, e.g. `ProcessService#startWorkflow`. The ONLY class using `ProcessService`                           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the business code. Calls `Workflow` naming the business event, is called by `WorkflowTaskHandler`, never touches VanillaBP                   |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs                                 |
| `loan-approval/src/main/java/.../loanapproval/model/AggregateRepository.java`              | the Panache repository of the aggregate. VanillaBP recognises it and uses it to load and save the aggregate                                  |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the module's own configuration. It is loaded by its file name and wins over `application.yaml`                                               |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | starts a real workflow and waits for the effect of the task                                                                                  |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test                                                |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Everything specific to the use case belongs in the test which extends
`WorkflowModuleTest`, never in the base class. Every test class carries `@QuarkusTest` itself.
Inheriting it from the base class does not make the test a bean.

## Adding this blueprint to an existing project

1. If the project has no workflow module yet, add a Maven module named after the use case
   and create `src/main/resources/META-INF/workflow-module` containing its ID. If a workflow
   module already exists, use it and skip to step 3.
2. Add `io.vanillabp:vanillabp-quarkus-support` and the `jandex-maven-plugin` to that module,
   and `io.vanillabp:vanillabp-quarkus-integration` plus one BPMS (business process management
   system) adapter to the application module. Import `io.vanillabp:vanillabp-bom` in the parent
   POM next to the Quarkus BOM and omit the version of every VanillaBP dependency. The BPMS
   adapters are the exception. They are released on their own, apart from the platform, so each
   names its own version. Without the index, nothing finds the module's `@WorkflowService`
   class.
3. Put the BPMN file into `src/main/resources/<workflow-module-id>/processes/<adapter-id>/`.
   The adapter ID is the configured one. By default it is the adapter type.
4. Add the workflow aggregate as a JPA entity with the natural ID as `@Id`, plus a repository
   bean for it (Panache, Panache Mongo or Spring Data), which VanillaBP recognises. If the
   persistence fits none of those, it needs a bean implementing `AggregatePersistenceAware`,
   which always wins. If the project already has an entity for this business case, use it
   instead of adding a second one.
5. Add `WorkflowTaskHandler` with the `@WorkflowService` annotation and one `@WorkflowTask`
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
6. Add GET endpoints starting the process and showing the aggregate.
7. Copy `LoanApprovalIT` and adapt it to the use case. A test in the workflow module needs no
   configuration of its own except a database. VanillaBP looks for the BPMN below the module's
   ID, both when the module is a dependency and when it is the artifact being run.

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
