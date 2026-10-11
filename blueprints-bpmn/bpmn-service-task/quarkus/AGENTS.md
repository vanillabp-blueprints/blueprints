# bpmn-service-task

Adds a service task which calls a surrounding system, with the three outcomes such a task can
have: done, retry, BPMN error. A delta on top of `module-single`.

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

Blueprint-specific names, each occurring in more than one place:

|          Name          |                                            Where it occurs                                             |
|------------------------|--------------------------------------------------------------------------------------------------------|
| `retrieveCreditRating` | the `@WorkflowTask` method, the Camunda 7 expression `${retrieveCreditRating}`, the Camunda 8 job type |
| `loan-rejected`        | the constant `Service.LOAN_REJECTED` and the `errorCode` of `bpmn:error` in the model                  |
| `ratingProvider`       | the input mapping in the BPMN and the `@TaskParam` of the handler                                      |

The error code is the contract between code and model. If the two drift apart, the boundary
event does not catch the `TaskException`. The workflow then ends as an incident instead of
taking the error path.

## Core files

|                                            File                                            |                                                      Why it matters                                                      |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | service task with an input mapping and an error boundary event referencing `bpmn:error` with `errorCode="loan-rejected"` |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method: aggregate plus `@TaskParam`, calls `Service`, catches nothing. No `@Transactional`           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the work: return early if rated, ask the provider, throw `TaskException(LOAN_REJECTED)` after writing the rejection      |
| `loan-approval/src/main/java/.../loanapproval/CreditRatingClient.java`                     | the port to the surrounding system, so a test can put a simulator in its place                                           |
| `loan-approval/src/main/java/.../loanapproval/CreditRatingUnavailableException.java`       | the technical failure. Because nothing catches it, the BPMS retries                                                      |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `creditRating`, `ratedBy`, `rejectionReason`                                                                             |
| `loan-approval/src/test/java/.../CreditRatingSimulator.java`                               | the provider under test control, extending the harness class `Simulator`                                                 |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per outcome: rated, retried, rejected                                                                           |

## Boilerplate files

|                                    File                                     |                                       Purpose                                        |
|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                  | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                                     | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                                       | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                           | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`                         | the database of the module's own test                                                |
| `loan-approval/src/main/java/.../loanapproval/LocalCreditRatingClient.java` | stand-in provider, so the blueprint runs alone. Replace it with the real client      |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                | what the application tells the process. Unchanged from the base blueprint            |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`           | GET endpoints operating the process                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                   | base class of the integration test: waits for workflow progress                      |
| `loan-approval/src/test/java/.../Simulator.java`                            | base class of a stand-in for a surrounding system                                    |
| `application/src/test/java/.../ApplicationSmokeTest.java`                   | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                                    | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are identical in every blueprint.
Copy them unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the
base class does not make the test a bean.

## Adding this blueprint to an existing project

1. Add the service task to the BPMN. The task may end the business case in a way the process
   has to react to. Then attach an error boundary event, and declare a `bpmn:error` whose
   `errorCode` is the string the code will throw.
2. Add an interface for the surrounding system the task talks to, in the workflow module.
   Add the real client as its implementation. Do not call the system from
   `WorkflowTaskHandler`.
3. Add the `@WorkflowTask` method to `WorkflowTaskHandler`, named after the task definition. It
   takes the aggregate, adds a `@TaskParam` per input mapping the BPMN provides, calls
   `Service` and does nothing else. Never annotate it with `@Transactional`, because that fails
   the boot. Never catch what the business code throws.
4. Add the business method to `Service`. Start it with a check on the aggregate which returns
   early if the work was already done. Throw `TaskException(<error code>)` for an outcome the
   BPMN models, after you wrote to the aggregate what the process needs to know. Let technical
   failures through untouched.
5. Extend the workflow aggregate by the attributes the task writes.
6. Add a simulator for the surrounding system to the test sources. It extends `Simulator` and
   is annotated `@Alternative`, `@Priority(1)` and `@ApplicationScoped`. So it takes the place
   of the real client while the test runs.
7. Copy `LoanApprovalIT` and write one test per outcome the task has.

If the project already has a business service for this use case, add the methods there instead
of creating a second one. Keep `@Transactional` off the methods a task handler calls. VanillaBP
cannot see such an annotation while booting. But the task fails when it runs, with a message
which names the workflow module, the process, the task and the handler method.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

All three tests of `LoanApprovalIT` have to pass. They prove the aspect:

- the aggregate holds the rating and the provider from the BPMN input mapping,
- a refused request is followed by a second one, so the BPMS (business process management
  system) did retry,
- a rejected loan keeps its `rejectionReason`. That proves that a `TaskException` commits.

You no longer have to guess a `@Transactional` which reaches the task from a failing assertion.
On the handler it fails the boot. On a bean the handler calls it fails the task. Both messages
name the method. If a task never runs, the wiring between BPMN and code is wrong. The startup
log then names the BPMN task without a method, or the method without a task.

Do not report success without having run this.
