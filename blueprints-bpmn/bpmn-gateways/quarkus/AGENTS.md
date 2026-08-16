# bpmn-gateways

Adds a decision to the process: an exclusive gateway routing on an attribute of the
workflow aggregate, with a default flow. A delta on top of `module-single`.

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

|                                        Name                                        |                                           Where it occurs                                            |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `ratedAcceptable`, `ratedForManualReview`                                          | the getters `isRatedAcceptable()`/`isRatedForManualReview()` and the conditions of the first gateway |
| `acceptable`, `review`, `too-low`                                                  | the values `Service#bandOf` returns and the values those getters compare against                     |
| `approveLoan`, `requestManualReview`, `rejectLoan`, `sendPaperLetter`, `sendEmail` | one `@WorkflowTask` method and one task definition per branch                                        |

A getter's name is the contract between code and model. Renaming one without the condition
sends every workflow down the default flow, which looks like a business decision rather than
a defect.

## Core files

|                                            File                                            |                                                                             Why it matters                                                                             |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | two exclusive gateways, each with conditional flows and a `default` one: the first asks the aggregate, the second reads a raw attribute and is the version NOT to copy |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `ratingBand` plus the two intention-revealing getters the first gateway asks                                                                                           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `bandOf` turns the rating into a band using the configured thresholds; one method per branch                                                                           |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@WorkflowTask` method per branch                                                                                                                                  |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the thresholds; a number a condition would otherwise carry                                                                                                             |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per branch, each steered by the amount alone                                                                                                                  |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test, and where that test reads its BPMN from       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Decide what the process may branch on and add ONE attribute for it to the workflow
   aggregate, holding a small set of allowed values.
2. Add a getter per branch which answers the question the gateway asks, e.g.
   `isRatedAcceptable()`. **The conditions reference those getters, never the attribute.**
   This is the pattern the wiki calls
   [Decoupling BPMN from the data model](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#decoupling-bpmn-from-the-data-model)
   and strongly recommends: the data model may then change without touching the BPMN and
   without migrating running workflows. Getters derived this way cannot overlap either,
   which a boolean per branch can.
3. Let the business code derive the attribute. Thresholds, tables and rules belong into
   `Service` and into configuration, never into a condition of the model - a changed number
   would otherwise be a new process version with instances left on the old one.
4. Add the gateway to the BPMN and one conditional sequence flow per branch, in the
   expression language of the engine: `${ratedAcceptable}` for Camunda 7, `=ratedAcceptable`
   for Camunda 8. The Java code is the same for both.
5. Give the gateway a `default` flow. Without one a workflow whose value fits no condition
   stops at the gateway, and on some engines that is an incident nobody expects.
6. Add a `@WorkflowTask` method per branch, each forwarding to `Service` as everywhere else.
7. Copy `LoanApprovalIT` and write one test per branch, including the default flow. The
   default flow is the branch most likely to be wrong, because nothing names it in the code.

The second gateway of this blueprint (`Gateway_Notification`, condition `${amount >= 10000}`)
does none of that on purpose: it reads a raw attribute and carries its threshold in the
model. It is in the blueprint to be recognized in an existing process, not to be copied into
a new one, and both the model and the README say so.

If the value a condition needs is not on the aggregate, that is the finding: put it there
rather than pushing a process variable, and it stays visible to every other part of the
application.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass: three amounts, three branches, one
outcome each. Run it on both BPMS if you touched a condition - the expression languages
differ, and a condition that never holds sends every workflow down the default flow, which
no build tells you about unless a test asks for the other branches.

Do not report success without having run this.
