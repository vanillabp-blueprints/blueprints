# bpmn-gateways

Adds a decision to the process: an exclusive gateway which routes on an attribute of the
workflow aggregate, with a default flow. A delta on top of `module-single`.

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

|                                        Name                                        |                                           Where it occurs                                            |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `ratedAcceptable`, `ratedForManualReview`                                          | the getters `isRatedAcceptable()`/`isRatedForManualReview()` and the conditions of the first gateway |
| `acceptable`, `review`, `too-low`                                                  | the values `Service#bandOf` returns and the values those getters compare against                     |
| `approveLoan`, `requestManualReview`, `rejectLoan`, `sendPaperLetter`, `sendEmail` | one `@WorkflowTask` method and one task definition per branch                                        |

A getter's name is the contract between code and model. If you rename one but not the
condition, every workflow takes the default flow. That looks like a business decision, not like
a defect.

## Core files

|                                            File                                            |                                                                             Why it matters                                                                             |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | two exclusive gateways, each with conditional flows and a `default` one. The first asks the aggregate. The second reads a raw attribute and is the version NOT to copy |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `ratingBand` plus the two intention-revealing getters the first gateway asks                                                                                           |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `bandOf` turns the rating into a band using the configured thresholds. One method per branch                                                                           |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@WorkflowTask` method per branch                                                                                                                                  |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the thresholds. Otherwise a condition would carry these numbers                                                                                                        |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per branch, each steered by the amount alone                                                                                                                  |

## Boilerplate files

|                               File                                |                                             Purpose                                             |
|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles and the VanillaBP BOM import                                                  |
| `loan-approval/pom.xml`                                           | `vanillabp-spring-boot-support`, never an adapter                                               |
| `application/pom.xml`                                             | the BPMS adapter, the only place a BPMS is named                                                |
| `application/src/main/java/.../Application.java`                  | the Spring Boot application, in the parent package of the module                                |
| `application/src/main/resources/application.yaml`                 | the datasource, and the profile the Maven build filters in                                      |
| `application/src/main/resources/application-camunda7.yaml`        | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                             |
| `loan-approval/src/test/java/.../TestApplication.java`            | the minimal application the module's test boots                                                 |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                                 |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                                  |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model                       |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged.

## Adding this blueprint to an existing project

1. Decide what the process may branch on. Add ONE attribute for it to the workflow aggregate,
   with a small set of allowed values.
2. Add a getter per branch which answers the question the gateway asks, for example
   `isRatedAcceptable()`. **The conditions refer to those getters, never to the attribute.**
   The wiki calls this pattern
   [Decoupling BPMN from the data model](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#decoupling-bpmn-from-the-data-model)
   and strongly recommends it. The data model may then change without touching the BPMN and
   without migrating running workflows. Getters derived this way cannot overlap either. A
   boolean per branch can.
3. Let the business code derive the attribute. Thresholds, tables and rules belong in `Service`
   and in configuration, never in a condition of the model. Otherwise a changed number would be
   a new process version, with instances left on the old one.
4. Add the gateway to the BPMN, and one conditional sequence flow per branch, in the expression
   language of the engine: `${ratedAcceptable}` for Camunda 7, `=ratedAcceptable` for Camunda
   8. The Java code is the same for both.
5. Annotate the aggregate class with `@NoSyncWithBPMS`. Put `@SyncWithBPMS` on exactly what the
   conditions read: the getters of step 2, and any raw attribute a condition still names. A
   condition can only read what was shared. So a missing annotation shows up as a workflow
   which takes the wrong branch, not as a message at startup.
6. Give the gateway a `default` flow. Without one, a workflow whose value fits no condition
   stops at the gateway. On some engines that is an incident nobody expects.
7. Add a `@WorkflowTask` method per branch. Each forwards to `Service`, as everywhere else.
8. Copy `LoanApprovalIT` and write one test per branch, including the default flow. The default
   flow is the branch which is most likely wrong, because nothing in the code names it.

The second gateway of this blueprint (`Gateway_Notification`, condition `${amount >= 10000}`)
does none of that, on purpose. It reads a raw attribute and carries its threshold in the model.
It is in the blueprint so that you recognize it in an existing process, not so that you copy it
into a new one. Both the model and the README say so.

If the value a condition needs is not on the aggregate, that is the finding. Put it there
instead of pushing a process variable. Then every other part of the application can see it too.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass: three amounts, three branches, one outcome
each. Run it on both BPMS (business process management systems) if you touched a condition. The
expression languages differ. A condition which is never true sends every workflow down the
default flow. No build tells you about that, unless a test asks for the other branches.

Do not report success without having run this.
