# bpmn-aggregate-decoupling

Keeps the BPMN independent of the data model. The conditions of the process name getters which
answer questions. `@NoSyncWithBPMS` plus `@SyncWithBPMS` make sure that only those answers
reach the BPMS (business process management system). A delta on top of `module-single`.

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

|                               Name                               |                                             Where it occurs                                              |
|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `approvableWithoutReview`, `worthAManualReview`                  | the getters `isApprovableWithoutReview()`/`isWorthAManualReview()` and the conditions of the gateway     |
| `RiskClass`, `LOW`, `MEDIUM`, `HIGH`                             | the enum those getters compare against, and what `Service#riskClassOf` returns. No model names any of it |
| `assessRisk`, `approveLoan`, `requestManualReview`, `rejectLoan` | one `@WorkflowTask` method and one task definition per service task                                      |

A getter's name is the contract between code and model. The annotation on it is the contract
with the BPMS. If you rename a getter but not the condition, every workflow takes the default
flow. That looks like a business decision, not like a defect. Dropping the `@SyncWithBPMS` does
the same on a BPMS which evaluates conditions on its own copy of the data.

## Core files

|                                            File                                            |                                                    Why it matters                                                    |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | an exclusive gateway with a `default` flow whose conditions name two getters and no attribute                        |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `@NoSyncWithBPMS` on the class, `@SyncWithBPMS` on the two getters, and the attributes which stay in the application |
| `loan-approval/src/main/java/.../loanapproval/model/RiskClass.java`                        | the data model no condition names, which is what makes it replaceable                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `riskClassOf` derives the risk class from the rating and the configured thresholds. One method per branch            |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one `@WorkflowTask` method per service task                                                                          |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the thresholds. Otherwise a condition would carry these numbers                                                      |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per risk class, plus one showing the process runs without the customer's name                               |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test                                                |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean.

## Adding this blueprint to an existing project

1. Write down the questions the process asks, in business terms: "may this be approved without
   a review?", not "is the risk class LOW?". The model may know those questions and nothing
   else.
2. Add a getter per question to the workflow aggregate. Let it answer from whatever data the
   application holds. **The conditions refer to those getters, never to an attribute.** The
   wiki calls this pattern
   [Decoupling BPMN from the data model](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#decoupling-bpmn-from-the-data-model)
   and strongly recommends it. The data model may then change without touching the BPMN and
   without migrating running workflows.
3. Derive the questions from ONE value, so no two conditions of a gateway can be true at once.
   A boolean per branch can overlap. Which branch an engine then takes differs between engines.
4. Annotate the getters with `@SyncWithBPMS`. A BPMS which keeps its own copy of the data
   evaluates the condition on what VanillaBP shared with it. If nobody shares a getter, nobody
   can answer its condition.
5. Annotate the class with `@NoSyncWithBPMS`, and let the annotations on the getters be the
   exception. Then everything a process does not ask for stays in the application, personal
   data included. The BPMS holds the answers and the id of the aggregate.
6. Make that id a natural id of the business case. It is the one value every BPMS gets. So a
   business identifier there lets you detect a duplicate start
   ([natural ids](https://github.com/vanillabp/spi-for-java#natural-ids)).
7. Let the business code derive the data behind the answers. Thresholds, tables and rules
   belong in `Service` and in configuration, never in a condition of the model.
8. Add the conditions in the expression language of the engine: `${approvableWithoutReview}`
   for Camunda 7, `=approvableWithoutReview` for Camunda 8. The Java code is the same for
   both.
9. Copy `LoanApprovalIT` and write one test per branch, the default flow included. Then change
   the data behind an answer, for example an enum instead of a boolean. Run the test again
   without touching the BPMN. This blueprint is about that property.

A nested value shared with a BPMS travels as an object variable, in the serialization format
the adapter is configured with. Make that decision on purpose. Questions answered by a getter
are booleans, so this blueprint needs none of it.

If the value a condition needs is not on the aggregate, that is the finding. Put it there
instead of pushing a process variable. Then every other part of the application can see it too.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass: three amounts, three risk classes, one
outcome each, and the customer's name still in the database. Run it on both BPMS if you touched
a condition or an annotation. The expression languages differ. A condition which is never true
sends every workflow down the default flow. No build tells you about that, unless a test asks
for the other branches.

Do not report success without having run this.
