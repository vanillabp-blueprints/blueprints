# bpmn-call-activity-decomposition

Splits one process into a calling and a called process, linked by a call activity. Both work on
the same workflow aggregate. A delta on top of `module-single`.

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
| `loan_approval`            | BPMN process ID of the calling process                                                                                    |

Blueprint-specific names, each occurring in more than one place:

|                Name                 |                                                              Where it occurs                                                              |
|-------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `risk_assessment`                   | the ID of the called process, the file name of its BPMN, `calledElement` respectively `zeebe:calledElement`, and `secondaryBpmnProcesses` |
| `checkCollateral`, `checkDebtRatio` | the tasks of the called process: one task definition and one `@WorkflowTask` method each                                                  |
| `decideOnLoan`                      | the task of the calling process after the call activity                                                                                   |

The ID of the called process occurs twice in the model and once in the code. If a rename misses
one of the three, the boot fails, because VanillaBP checks the wiring between BPMN and code
while starting.

## Core files

|                                             File                                             |                                                           Why it matters                                                           |
|----------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn`   | the call activity, including what it has to pass to the called process on this engine                                              |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/risk_assessment.bpmn` | the called process: a plain start event, its tasks, an end event which returns to the call activity                                |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                      | one `@WorkflowService` naming both processes, `secondaryBpmnProcesses` for the called one, and the `@WorkflowTask` methods of both |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                          | the single aggregate both processes work on                                                                                        |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                  | the business code, which does not know that the model is split                                                                     |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                        | starts the calling process and asserts what the called one wrote                                                                   |

## Boilerplate files

|                                File                                 |                                             Purpose                                             |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles and the VanillaBP BOM import                                                  |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support`, never an adapter                                               |
| `application/pom.xml`                                               | the BPMS adapter, the only place a BPMS is named                                                |
| `application/src/main/java/.../Application.java`                    | the Spring Boot application, in the parent package of the module                                |
| `application/src/main/resources/application.yaml`                   | the datasource, and the profile the Maven build filters in                                      |
| `application/src/main/resources/application-camunda7.yaml`          | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | GET endpoints operating the process                                                             |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`        | starts the calling process; a called process is never started from code                         |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the numbers the checks and the decision use                                                     |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                                                 |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                                 |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                                  |
| `docs/loan_approval.png`, `docs/risk_assessment.png`                | the pictures of the two processes the README shows, rendered from the BPMN models               |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged.

## Adding this blueprint to an existing project

1. Decide which section of the process moves out. Use a call activity when the section is part
   of the same business case and was split off only to keep a diagram readable. If several
   unrelated processes use the section, it needs a workflow aggregate of its own. Then it is
   not a call activity. Model it as a collapsed pool and start it from a service task.
2. Put the called process into a BPMN file of its own, in the same `processes/<adapter-id>/`
   directory of the same workflow module. Give it a plain start event. The call activity starts
   it, so no code and no message takes part.
3. Add the call activity to the calling process and let it address the called process by
   its ID (`calledElement` on Camunda 7, `zeebe:calledElement processId` on Camunda 8).
4. Hand nothing over. The workflow aggregate is the state of the business case, and both
   processes work on the same one. So the call activity needs no input mapping. The adapter
   makes sure the called instance finds that aggregate. On Camunda 7 the business key is passed
   on while the model is deployed. On Camunda 8 `propagateAllParentVariables` stays at its
   default (`true`), and that carries the variable which holds the aggregate's ID.
5. Name the called process in `secondaryBpmnProcesses` of the existing `@WorkflowService`, and
   add its `@WorkflowTask` methods to the same class. **Do not create a second class annotated
   with `@WorkflowService` which declares the called process as its `bpmnProcess`.** VanillaBP
   builds one `ProcessService` per aggregate class, so only one process can be the one
   `startWorkflow` starts. With two of them the boot fails and names both classes.
6. Keep working on the one aggregate. Do not add a second entity for the called process, and do
   not pass data between the processes. There is nothing to pass, because both read and write
   the same aggregate.
7. Extend the integration test instead of writing a second one. A test knows the business case,
   not the number of process instances behind it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass: the attributes written by the called
process, and the decision the calling process makes from them. Run it on both BPMS (business
process management systems) after you touched the call activity. What a call activity has to
pass to the called process is the one thing which differs between the engines. From the
outside, a workflow which stops inside the called process looks the same on both.

Do not report success without having run this.
