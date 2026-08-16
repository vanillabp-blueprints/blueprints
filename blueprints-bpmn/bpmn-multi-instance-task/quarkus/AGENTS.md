# bpmn-multi-instance-task

Runs one task once per element of a collection and hands each instance its element, its index
and the total. A delta on top of `module-single`.

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

|               Name                |                                            Where it occurs                                             |
|-----------------------------------|--------------------------------------------------------------------------------------------------------|
| `ServiceTask_RequestPartnerOffer` | the ID of the multi-instance element in the BPMN and the value of all three multi-instance annotations |
| `partnerIds`                      | the attribute of the workflow aggregate and the collection expression of the multi-instance element    |
| `partnerId`                       | the element variable of the model and the parameter the handler is given                               |

The value of the annotations is the ID of the multi-instance ELEMENT, not the name of the
element variable. Getting it wrong fails the task while it runs, with a message naming the
contexts the BPMS did supply.

## Core files

|                                            File                                            |                                                    Why it matters                                                     |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the multi-instance element: what is iterated over, what each instance is given, and whether they run at the same time |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | `@MultiInstanceElement`, `@MultiInstanceIndex`, `@MultiInstanceTotal`                                                 |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the collection the model iterates over, and the rows the iterations write                                             |
| `loan-approval/src/main/java/.../loanapproval/model/PartnerOffer.java`                     | a row per iteration, which is what makes concurrent instances harmless                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | one method per iteration and one for the result over all of them                                                      |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the elements, so the number of iterations is configuration rather than model                                          |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | asserts every row, in no particular order                                                                             |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test                                                |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`      | starts the workflow                                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Put what is iterated over onto the workflow aggregate, as identifiers rather than objects,
   and let the business code fill it in a task BEFORE the multi-instance one. The collection
   has to exist when the engine reaches the task.
2. Add the multi-instance element to the BPMN and let it read that attribute
   (`camunda:collection="${partnerIds}"` on Camunda 7, `inputCollection="=partnerIds"` on
   Camunda 8). Never push a collection into the process as a variable.
3. Annotate the parameters of the `@WorkflowTask` method with `@MultiInstanceElement`,
   `@MultiInstanceIndex` or `@MultiInstanceTotal`, whichever the business code needs, and give
   each of them **the ID of the multi-instance element**.
4. Write the result of an iteration as a ROW OF ITS OWN, never into an attribute all
   iterations share. Instances run at the same time on a remote engine, each of them saves the
   whole aggregate, and the one committing last would put back what it read at its start.
   Nothing reports that.
5. Put anything computed over all iterations into a task AFTER the multi-instance one. An
   iteration sees one element and cannot know whether it is the last.
6. Keep the number of iterations out of the model. A list in configuration or in the data
   means adding an element is not a deployment.
7. Extend `LoanApprovalIT` with assertions that do not depend on the order of the iterations.
   `containsExactlyInAnyOrder` rather than `containsExactly`, because there is no order.

Nested iterations, a multi-instance task inside a multi-instance subprocess, are what the
`resolverBean` of `@MultiInstanceElement` is for. This blueprint does not need it; the
[SPI documentation](https://github.com/vanillabp/spi-for-java#multi-instance) shows it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass: one row per element, the index each
iteration was given, and the result computed after the last of them.

Do not report success without having run this.
