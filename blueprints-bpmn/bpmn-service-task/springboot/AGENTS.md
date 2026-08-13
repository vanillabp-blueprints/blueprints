# bpmn-service-task

Adds a service task that calls a surrounding system, together with the three outcomes such
a task can have: done, retry, BPMN error. A delta on top of `module-single`.

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

Blueprint-specific names, each occurring in more than one place:

|          Name          |                                            Where it occurs                                             |
|------------------------|--------------------------------------------------------------------------------------------------------|
| `retrieveCreditRating` | the `@WorkflowTask` method, the Camunda 7 expression `${retrieveCreditRating}`, the Camunda 8 job type |
| `loan-rejected`        | the constant `Service.LOAN_REJECTED` and the `errorCode` of `bpmn:error` in the model                  |
| `ratingProvider`       | the input mapping in the BPMN and the `@TaskParam` of the handler                                      |

The error code is the contract between code and model. If the two drift apart, the
`TaskException` is not caught by the boundary event and the workflow ends as an incident
instead of taking the error path.

## Core files

|                                            File                                            |                                                      Why it matters                                                      |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | service task with an input mapping and an error boundary event referencing `bpmn:error` with `errorCode="loan-rejected"` |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method: aggregate plus `@TaskParam`, calls `Service`, catches nothing. No `@Transactional`           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the work: return early if rated, ask the provider, throw `TaskException(LOAN_REJECTED)` after writing the rejection      |
| `loan-approval/src/main/java/.../loanapproval/CreditRatingClient.java`                     | the port to the surrounding system, so a test can put a simulator in its place                                           |
| `loan-approval/src/main/java/.../loanapproval/CreditRatingUnavailableException.java`       | the technical failure; leaving it uncaught is what makes the BPMS retry                                                  |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `creditRating`, `ratedBy`, `rejectionReason`                                                                             |
| `loan-approval/src/test/java/.../CreditRatingSimulator.java`                               | the provider under test control, extending the harness class `Simulator`                                                 |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per outcome: rated, retried, rejected                                                                           |

## Boilerplate files

|                                    File                                     |                                           Purpose                                           |
|-----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                  | the BPMS profiles and the VanillaBP BOM import                                              |
| `loan-approval/pom.xml`                                                     | `vanillabp-spring-boot-support`, never an adapter                                           |
| `application/pom.xml`                                                       | the BPMS adapter, the only place a BPMS is named                                            |
| `application/src/main/java/.../Application.java`                            | the Spring Boot application, in the parent package of the module                            |
| `application/src/main/resources/application.yaml`                           | the datasource, and the optional import of the file below                                   |
| `application/src/main/camunda7/resources/camunda7-webapps.yaml`             | the demo user of Camunda's web applications; on the classpath in the Camunda 7 profile only |
| `loan-approval/src/main/java/.../loanapproval/LocalCreditRatingClient.java` | stand-in provider so the blueprint runs alone; replace it with the real client              |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                | what the application tells the process; unchanged from the base blueprint                   |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`           | GET endpoints operating the process                                                         |
| `loan-approval/src/test/java/.../TestApplication.java`                      | the minimal application the module's test boots                                             |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                   | base class of the integration test: waits for workflow progress                             |
| `loan-approval/src/test/java/.../Simulator.java`                            | base class of a stand-in for a surrounding system                                           |
| `application/src/test/java/.../ApplicationSmokeTest.java`                   | boots the application, which validates the BPMN-to-code wiring                              |
| `docs/loan_approval.png`                                                    | the picture of the process the README shows, rendered from the BPMN model                   |

`TestApplication`, `WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are
identical in every blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Add the service task to the BPMN. If the task may end the business case in a way the
   process has to react to, attach an error boundary event and declare a `bpmn:error` whose
   `errorCode` is the string the code will throw.
2. Add an interface for the surrounding system the task talks to, in the workflow module.
   Add the real client as its implementation. Do not call the system from
   `WorkflowTaskHandler`.
3. Add the `@WorkflowTask` method to `WorkflowTaskHandler`, named after the task
   definition. It takes the aggregate, adds a `@TaskParam` per input mapping the BPMN
   provides, calls `Service` and does nothing else. Never annotate it with
   `@Transactional`, which fails the boot, and never catch what the business code throws.
4. Add the business method to `Service`. Start it with a check on the aggregate that
   returns early if the work has been done already. Throw `TaskException(<error code>)` for
   an outcome the BPMN models, after writing to the aggregate what the process needs to
   know. Let technical failures through untouched.
5. Extend the workflow aggregate by the attributes the task writes.
6. Add a simulator for the surrounding system to the test sources, extending `Simulator`,
   exposed as a `@Primary` bean of a `@TestConfiguration` inside the test.
7. Copy `LoanApprovalIT` and write one test per outcome the task has.

If the project already has a business service for this use case, add the methods there
rather than creating a second one, and keep `@Transactional` off the methods a task handler
calls. VanillaBP cannot see such an annotation while booting, but the task fails when it
runs, with a message naming the workflow module, the process, the task and the handler
method.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

All three tests of `LoanApprovalIT` have to pass. They are the proof of the aspect:

- the aggregate carries the rating and the provider from the BPMN input mapping,
- a refused request is followed by a second one, so the BPMS did retry,
- a rejected loan keeps its `rejectionReason`, which is what proves that a `TaskException`
  commits.

A `@Transactional` reaching the task no longer needs to be guessed from a failing assertion:
on the handler it fails the boot, on a bean the handler calls it fails the task, and both
messages name the method. If a task is never executed, the wiring between BPMN and code is
wrong, and the startup log names which BPMN task has no method or which method has no task.

Do not report success without having run this.
