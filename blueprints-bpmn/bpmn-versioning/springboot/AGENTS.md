# bpmn-versioning

Adds versions: one task definition served by two `@WorkflowTask` methods, each for the
versions of the process it belongs to. A workflow keeps the code of the version it was
started on. A delta on top of `module-single`.

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

Blueprint-specific names:

|     Name     |                                           Where it occurs                                           |
|--------------|-----------------------------------------------------------------------------------------------------|
| `assessRisk` | the task definition in the BPMN and the `taskDefinition` of BOTH handler methods                    |
| `version`    | the attribute deciding which method serves a delivered task: `"1"`, `">1"`, `"1-3"`, `"v1.0..v2.0"` |

The version is the one the BPMS counts per BPMN process id, or a version tag from the model.
It is never a version the application invents, and it is never stored on the aggregate.

## Core files

|                                            File                                            |                                                     Why it matters                                                     |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | two methods, one `taskDefinition`, no overlapping version ranges. Overlaps fail the boot                               |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | one business method per version; the older one stays until the last workflow using it ended                            |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | attributes of BOTH versions. Removing what only the old version writes takes the data of the running workflows with it |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the task definition both methods refer to                                                                              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | asserts WHICH method ran, which is the only assertion proving the dispatch                                             |

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

1. Decide whether the change may reach the running workflows. A bug fix usually may, and then
   the existing method is edited and nothing here applies. A changed behaviour usually may
   not, and that is what the version range is for.
2. Keep the old method and add the new one next to it, both with the same `taskDefinition`
   and with version ranges which do not overlap. Name them after what they do, not after
   their version: `assessRiskManually` rather than `assessRiskV1`.
3. Keep the business method of the old version as well. It is called by workflows which are
   still running, and it may read attributes no new workflow writes.
4. Keep those attributes on the aggregate. The aggregate is the state of the business case,
   not of the current release.
5. Deploy the new model and the new method together. The BPMS counts the version up, running
   workflows keep theirs, new ones get the new one, and nothing is migrated.
6. Once no workflow runs on a version any more, either delete its method or declare the
   version obsolete with `vanillabp.adapters.<id>.outfaded-versions`. VanillaBP reports at
   startup which versions still have workflows and which of their task definitions no method
   serves, so this is a decision it reminds you of rather than one to remember.
7. Copy `LoanApprovalIT` and assert WHICH method ran. A test which only asserts that the task
   ran passes whichever method served it, and that is the mistake this blueprint is about.

Never store the process version on the workflow aggregate to branch on it in the business
code. That is the same `if` in a different place, and it drifts from what the BPMS actually
runs.

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
