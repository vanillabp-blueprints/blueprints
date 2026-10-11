# bpmn-versioning

Adds versions: two `@WorkflowTask` methods serve one task definition, each for the versions of
the process it belongs to. A workflow keeps the code of the version it was started on. A delta
on top of `module-single`.

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

Blueprint-specific names:

|     Name     |                                             Where it occurs                                              |
|--------------|----------------------------------------------------------------------------------------------------------|
| `assessRisk` | the task definition in the BPMN and the `taskDefinition` of BOTH handler methods                         |
| `version`    | the attribute which decides which method serves a delivered task: `"1"`, `">1"`, `"1-3"`, `"v1.0..v2.0"` |

The version is the one the BPMS (business process management system) counts per BPMN process
id, or a version tag from the model. The application never invents a version, and it never
stores one on the aggregate.

## Core files

|                                            File                                            |                                                     Why it matters                                                     |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | two methods, one `taskDefinition`, no overlapping version ranges. Overlaps fail the boot                               |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | one business method per version. The older one stays until the last workflow which uses it has ended                   |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | attributes of BOTH versions. Removing what only the old version writes takes the data of the running workflows with it |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the task definition both methods refer to                                                                              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | asserts WHICH method ran. Only that assertion proves the choice of method                                              |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test                                                |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean. Everything specific to the use case belongs in the test which
extends `WorkflowModuleTest`, never in the base class.

## Adding this blueprint to an existing project

1. Decide whether the change may reach the running workflows. A bug fix usually may. Then edit
   the existing method, and nothing here applies. A changed behaviour usually may not, and that
   is what the version range is for.
2. Keep the old method and add the new one next to it. Both have the same `taskDefinition` and
   version ranges which do not overlap. Name them after what they do, not after their version:
   `assessRiskManually`, not `assessRiskV1`.
3. Keep the business method of the old version as well. Workflows which still run call it, and
   it may read attributes no new workflow writes.
4. Keep those attributes on the aggregate. The aggregate is the state of the business case,
   not of the current release.
5. Deploy the new model and the new method together. The BPMS counts the version up. Running
   workflows keep theirs, new ones get the new one, and nothing is migrated.
6. Once no workflow runs on a version any more, either delete its method, or declare the
   version obsolete with `vanillabp.adapters.<id>.outfaded-versions`. VanillaBP reports at
   startup which versions still have workflows, and which of their task definitions no method
   serves. So VanillaBP reminds you of this decision, and you do not have to remember it.
7. Copy `LoanApprovalIT` and assert WHICH method ran. A test which only asserts that the task
   ran passes whichever method served it. That is the mistake this blueprint is about.

Never store the process version on the workflow aggregate to branch on it in the business code.
That is the same `if` in a different place, and it drifts apart from what the BPMS really runs.

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

There is no second-deployment test on this platform. A test here boots the application once, so
it cannot deploy a second version of the model.

Do not report success without having run this.
