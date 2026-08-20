# bpmn-aggregate-decoupling

Keeps the BPMN independent of the data model: the conditions of the process name getters
which answer questions, and `@NoSyncWithBPMS` plus `@SyncWithBPMS` decide that only those
answers reach the BPMS. A delta on top of `module-single`.

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

|                               Name                               |                                             Where it occurs                                             |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `approvableWithoutReview`, `worthAManualReview`                  | the getters `isApprovableWithoutReview()`/`isWorthAManualReview()` and the conditions of the gateway    |
| `RiskClass`, `LOW`, `MEDIUM`, `HIGH`                             | the enum those getters compare against and what `Service#riskClassOf` returns; no model names any of it |
| `assessRisk`, `approveLoan`, `requestManualReview`, `rejectLoan` | one `@WorkflowTask` method and one task definition per service task                                     |

A getter's name is the contract between code and model, and the annotation on it is the
contract with the BPMS. Renaming a getter without the condition sends every workflow down the
default flow, which looks like a business decision rather than a defect. Dropping the
`@SyncWithBPMS` does the same on a BPMS which evaluates conditions on its own copy of the
data.

## Core files

|                                            File                                            |                                                    Why it matters                                                    |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | an exclusive gateway with a `default` flow whose conditions name two getters and no attribute                        |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `@NoSyncWithBPMS` on the class, `@SyncWithBPMS` on the two getters, and the attributes which stay in the application |
| `loan-approval/src/main/java/.../loanapproval/model/RiskClass.java`                        | the data model no condition names, which is what makes it replaceable                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `riskClassOf` derives the risk class from the rating and the configured thresholds; one method per branch            |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@WorkflowTask` method per service task                                                                          |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the thresholds; numbers a condition would otherwise carry                                                            |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per risk class, plus one showing the process runs without the customer's name                               |

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

1. Write down the questions the process asks, in business terms: "may this be approved
   without a review?" rather than "is the risk class LOW?". Those questions are what the
   model is allowed to know.
2. Add a getter per question to the workflow aggregate and let it answer from whatever data
   the application happens to hold. **The conditions reference those getters, never an
   attribute.** This is the pattern the wiki calls
   [Decoupling BPMN from the data model](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#decoupling-bpmn-from-the-data-model)
   and strongly recommends: the data model may then change without touching the BPMN and
   without migrating running workflows.
3. Derive the questions from ONE value, so no two conditions of a gateway can be true at
   once. A boolean per branch can overlap, and which branch an engine then takes differs
   between engines.
4. Annotate the getters with `@SyncWithBPMS`. A BPMS which keeps its own copy of the data
   evaluates the condition on what VanillaBP shared with it, so a getter nobody shares is a
   condition nobody can answer.
5. Annotate the class with `@NoSyncWithBPMS` and let the annotations on the getters be the
   exception. Everything a process does not ask for then stays in the application, personal
   data included, and the BPMS holds the answers and the id of the aggregate.
6. Make that id a natural id of the business case. It is the one value every BPMS is given,
   so a business identifier there makes a duplicate start detectable
   ([natural ids](https://github.com/vanillabp/spi-for-java#natural-ids)).
7. Let the business code derive the data behind the answers. Thresholds, tables and rules
   belong into `Service` and into configuration, never into a condition of the model.
8. Add the conditions in the expression language of the engine: `${approvableWithoutReview}`
   for Camunda 7, `=approvableWithoutReview` for Camunda 8. The Java code is the same for
   both.
9. Copy `LoanApprovalIT` and write one test per branch, the default flow included. Then
   change the data behind an answer, an enum for a boolean for instance, and run the test
   again without touching the BPMN. That is the property this blueprint is about.

A nested value shared with a BPMS travels as an object variable in the serialization format
the adapter is configured with, which is a decision to make deliberately. Questions answered
by a getter are booleans, so this blueprint needs none of it.

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

`LoanApprovalIT` proves the aspect and has to pass: three amounts, three risk classes, one
outcome each, and the customer's name still in the database. Run it on both BPMS if you
touched a condition or an annotation. The expression languages differ, and a condition which
never holds sends every workflow down the default flow, which no build tells you about unless
a test asks for the other branches.

Do not report success without having run this.
