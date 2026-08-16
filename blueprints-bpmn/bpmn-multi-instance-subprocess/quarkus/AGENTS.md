# bpmn-multi-instance-subprocess

Runs a whole section of a process once per element, with a second iteration nested inside
it, and shows how a task says which of the active iterations it means. A delta on top of
`module-single`.

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

|               Name                |                                                         Where it occurs                                                          |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `SubProcess_AssessRegion`         | the ID of the multi-instance subprocess, the constant `IterationResolver.ASSESS_REGION` and the annotations of `summariseRegion` |
| `ServiceTask_RequestPartnerOffer` | the ID of the multi-instance task inside it, and the constant `IterationResolver.REQUEST_PARTNER_OFFER`                          |
| `regionIds`, `partnerIds`         | the attributes of the workflow aggregate and the collection expressions of the two levels                                        |
| `regionId`, `partnerId`           | the element variables of the model                                                                                               |

Every multi-instance annotation names the BPMN id of an ELEMENT, and inside a nested
iteration that is not decoration: it decides which of the active loops answers. The resolver
names the same ids in `getNames()`.

## Core files

|                                            File                                            |                                     Why it matters                                      |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the multi-instance subprocess, the multi-instance task in it and a plain task in it     |
| `loan-approval/src/main/java/.../loanapproval/IterationResolver.java`                      | builds one object out of every active iteration - the answer for a task in two loops    |
| `loan-approval/src/main/java/.../loanapproval/Iteration.java`                              | what that object is                                                                     |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | a task using the resolver, a task naming one element, and a task after the subprocess   |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | one collection per level, and the rows both levels write                                |
| `loan-approval/src/main/java/.../loanapproval/model/PartnerOffer.java`                     | a row per inner iteration, carrying the element of the outer one                        |
| `loan-approval/src/main/java/.../loanapproval/model/RegionResult.java`                     | a row per outer iteration                                                               |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | the two lists, so the number of iterations per level is configuration rather than model |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | asserts the rows of both levels, in no particular order                                 |

## Boilerplate files

|                               File                                |                                       Purpose                                        |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                        | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                           | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                             | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                 | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`               | the database of the module's own test, and where that test reads its BPMN from       |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | GET endpoints operating the process                                                  |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`      | starts the workflow                                                                  |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`         | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`         | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                          | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Use a multi-instance SUBPROCESS when a whole section repeats, and a multi-instance TASK
   when one step does. The choice is about the model; the business code is the same either
   way.
2. Put what each level iterates over onto the workflow aggregate, as identifiers, and fill
   both collections in a task BEFORE the subprocess is reached.
3. Give the subprocess its loop characteristics (`camunda:collection="${regionIds}"` on
   Camunda 7, `inputCollection="=regionIds"` on Camunda 8) and the task inside it its own.
4. In a task that belongs to ONE level, name that element in the annotations:
   `@MultiInstanceElement("SubProcess_AssessRegion")`. **A task which is not multi-instance
   itself still runs inside the iteration of the subprocess around it**, and this is how it
   learns which one.
5. In a task sitting in SEVERAL iterations, write a `MultiInstanceElementResolver` bean and
   name it with `@MultiInstanceElement(resolverBean = ...)`. It is handed every active
   context, keyed by element id and ordered outermost first, and returns one object. Asking
   for each value separately works too and gets long fast.
6. Write a row per iteration, never into an attribute the iterations share, and put the
   element of the enclosing iteration into that row - otherwise a result cannot be told
   apart from its siblings later.
7. Put a result over the inner loop into a task at the END of the outer one, and a result
   over the outer loop into a task AFTER the subprocess. An iteration cannot know whether it
   is the last.
8. Extend `LoanApprovalIT` with assertions that do not depend on order.
   `containsExactlyInAnyOrder` rather than `containsExactly`: with two levels there are more
   siblings, and still no order between them.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass: regions times partners offers, one
summary per region, and the decision over all of them. Run it on both BPMS if you touched
the nesting - the engines report an iteration in completely different ways, and only the
test says whether the values still arrive.

Do not report success without having run this.
