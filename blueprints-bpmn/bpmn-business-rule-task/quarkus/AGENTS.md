# bpmn-business-rule-task

The base blueprint plus a decision: a service task rates the loan request, a DMN decision
table decides on it, a gateway routes on what it decided, and the task behind the gateway
hands the result to the application. Build `module-single` first, then apply this delta.

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
`loan-approval.yaml` and its property prefix, the REST path, and the resources location in
`src/test/resources/application.yaml` of the test. A missed occurrence does not fail
the build. It makes VanillaBP report at startup that no BPMN file was found.

That resource directory is not decoration: workflow modules share one classpath, so **all**
resources of a module have to live in the one subdirectory named after its ID, and its
classes in one Java package of their own. Only `META-INF/workflow-module` sits outside.
Adding a resource at the classpath root works until a second module ships a file of the same
name.

`retrieveCreditRating` and `recordDecision` are the task definitions: the name of the
`@WorkflowTask` method, the Camunda 7 expression `${retrieveCreditRating}` and the Camunda 8
job type. Rename one in all places or in none.

`loan_decision` is the decision id. It appears in the DMN file and in the business rule task
of the BPMN of the SAME adapter directory (`camunda:decisionRef` respectively
`zeebe:calledDecision`), and the two have to keep naming each other. `approval` is the name
the decision's result travels under: the output column of the table, the result variable of
the business rule task, the gateway condition, the input mapping of the following task and
the `@TaskParam`.

## Core files

|                                            File                                            |                                                                Why it matters                                                                 |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/META-INF/workflow-module`                                | one line, the workflow module ID. Without it the JAR is not a workflow module                                                                 |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the process. One directory per adapter ID, because BPMN carries engine specific attributes                                                    |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.dmn`  | the decision table, deployed with the process by the boot. Per adapter ID as well: the expression language of a rule is the engine's          |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `@WorkflowService` binds the class to the BPMN process, `@WorkflowTask` binds a method to a task. Contains no business logic, calls `Service` |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | what the application tells the process, e.g. `ProcessService#startWorkflow`. The ONLY class using `ProcessService`                            |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the business code. Calls `Workflow` naming the business event, is called by `WorkflowTaskHandler`, never touches VanillaBP                    |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the workflow aggregate: a JPA entity with the natural ID as primary key, holding all state the process needs                                  |
| `loan-approval/src/main/java/.../loanapproval/model/AggregateRepository.java`              | the Panache repository of the aggregate. VanillaBP recognises it and loads and saves the aggregate through it                                 |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the module's own configuration, loaded by its file name and taking precedence over `application.yaml`                                         |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | starts a real workflow and waits for the effect of the task                                                                                   |

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

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy them
unchanged. Everything specific to the use case belongs into the test extending
`WorkflowModuleTest`, never into the base class. Every test class carries `@QuarkusTest`
itself; inheriting it from the base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. If the project has no workflow module yet, add a Maven module named after the use case
   and create `src/main/resources/META-INF/workflow-module` containing its ID. If a workflow
   module already exists, use it and skip to step 3.
2. Add `io.vanillabp:vanillabp-quarkus-support` and the `jandex-maven-plugin` to that module,
   and `io.vanillabp:vanillabp-quarkus-integration` plus one BPMS adapter to the application
   module. Import `io.vanillabp:vanillabp-bom` in the parent POM next to the Quarkus BOM and
   omit the version of every VanillaBP dependency. The BPMS adapters are the exception:
   they are released independently of the platform, so each names its own version. Without the index the module's
   `@WorkflowService` class is never found.
3. Put the BPMN file into `src/main/resources/<workflow-module-id>/processes/<adapter-id>/`.
   The adapter ID is the configured one, which defaults to the adapter type.
4. Add the workflow aggregate as a JPA entity with the natural ID as `@Id`, plus a
   repository bean for it (Panache, Panache Mongo or Spring Data), which VanillaBP
   recognises. A persistence fitting none of those needs a bean implementing
   `AggregatePersistenceAware`, which always wins. If the project already has an entity
   for this business case, use it instead of adding a second one.
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
7. Copy `LoanApprovalIT` and adapt it to the use case. A test living in the workflow module
   needs no configuration of its own beyond a database: VanillaBP looks for the BPMN below
   the module's ID whether the module is a dependency or the artifact being run.

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
