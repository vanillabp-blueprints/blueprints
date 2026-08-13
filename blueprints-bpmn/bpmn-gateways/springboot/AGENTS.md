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

|                        Name                        |                                  Where it occurs                                  |
|----------------------------------------------------|-----------------------------------------------------------------------------------|
| `ratingBand`                                       | the attribute of `Aggregate` and the conditions of both sequence flows            |
| `acceptable`, `review`, `too-low`                  | the values `Service#bandOf` returns and the values the conditions compare against |
| `approveLoan`, `requestManualReview`, `rejectLoan` | one `@WorkflowTask` method and one task definition per branch                     |

The values are the contract between code and model. A renamed band that reaches only one of
the two sends every workflow down the default flow, which looks like a business decision
rather than a defect.

## Core files

|                                            File                                            |                                           Why it matters                                           |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the exclusive gateway, two conditional sequence flows and the `default` attribute naming the third |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `ratingBand`: the one attribute the conditions read                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `bandOf` turns the rating into a band using the configured thresholds; one method per branch       |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@WorkflowTask` method per branch                                                              |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the thresholds; a number a condition would otherwise carry                                         |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per branch, each steered by the amount alone                                              |

## Boilerplate files

|                               File                                |                                           Purpose                                           |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                                              |
| `loan-approval/pom.xml`                                           | `vanillabp-spring-boot-support`, never an adapter                                           |
| `application/pom.xml`                                             | the BPMS adapter, the only place a BPMS is named                                            |
| `application/src/main/java/.../Application.java`                  | the Spring Boot application, in the parent package of the module                            |
| `application/src/main/resources/application.yaml`                 | the datasource, and the optional import of the file below                                   |
| `application/src/main/camunda7/resources/camunda7-webapps.yaml`   | the demo user of Camunda's web applications; on the classpath in the Camunda 7 profile only |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                         |
| `loan-approval/src/test/java/.../TestApplication.java`            | the minimal application the module's test boots                                             |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                             |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                              |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model                   |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Decide what the process may branch on and add it to the workflow aggregate as ONE
   attribute. Prefer a value with a small set of allowed values over a boolean per branch:
   two conditions that can be true at the same time leave the choice to the engine, and
   engines answer that differently.
2. Let the business code derive that attribute. Thresholds, tables and rules belong into
   `Service` and into configuration, never into a condition of the model - a changed number
   would otherwise be a new process version with instances left on the old one.
3. Add the gateway to the BPMN and one conditional sequence flow per branch, in the
   expression language of the engine: `${attribute == 'value'}` for Camunda 7,
   `=attribute = "value"` for Camunda 8. The Java code is the same for both.
4. Give the gateway a `default` flow. Without one a workflow whose value fits no condition
   stops at the gateway, and on some engines that is an incident nobody expects.
5. Add a `@WorkflowTask` method per branch, each forwarding to `Service` as everywhere else.
6. Copy `LoanApprovalIT` and write one test per branch, including the default flow. The
   default flow is the branch most likely to be wrong, because nothing names it in the code.

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
