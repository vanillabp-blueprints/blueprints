# persistence-parallel-branches

Adds a parallel gateway and, with it, a second token in the workflow. Two branches change
the same business case at the same time, and the data model is what keeps the later commit
from throwing away the earlier one. A delta on top of `module-single`.

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

|          Name          |                                       Where it occurs                                        |
|------------------------|----------------------------------------------------------------------------------------------|
| `awaitPartnerApproval` | the `@WorkflowTask` method, the Camunda 7 `camunda:formKey` and the Camunda 8 form reference |
| `requestDocuments`     | the `@WorkflowTask` method of the other branch and its task definition                       |
| `recordDocuments`      | the task reading the documents, in the transaction VanillaBP owns                            |
| `Gateway_Split`        | the element VanillaBP names in its startup warning about a second token                      |

**The rule this blueprint is built on:** while more than one token is in the process, nothing
writes the workflow aggregate itself. Every branch writes an entity of its own. Breaking the
rule produces no error, only lost data, which is why it is a rule and not a hint.

## Core files

|                                            File                                            |                                                          Why it matters                                                          |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the parallel gateway which splits and the one which joins; between them the two branches                                         |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | carries only what a single token writes, plus one relation per branch. NOT the id of the open task                               |
| `loan-approval/src/main/java/.../loanapproval/model/PartnerApproval.java`                  | the record of the branch the application finishes, with the id of its open task                                                  |
| `loan-approval/src/main/java/.../loanapproval/model/DocumentCheck.java`                    | the record of the branch the BPMS finishes                                                                                       |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | `recordDocuments` runs in VanillaBP's transaction, `approvePartnerRequest` in the application's; neither touches the other's row |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | one method per branch; the partner branch keeps its task open with `@TaskId`                                                     |
| `loan-approval/src/test/java/.../DocumentsSimulator.java`                                  | takes a few seconds, which keeps a branch inside its transaction while the other one is answered                                 |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | produces the overlap and asserts that both branches kept their result                                                            |

## Boilerplate files

|                                File                                 |                                       Purpose                                        |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                             | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                               | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                   | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`                 | the database of the module's own test, and where that test reads its BPMN from       |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                      |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | GET endpoints operating the process, including the one answering the waiting branch  |
| `loan-approval/src/main/java/.../loanapproval/DocumentsClient.java` | the port to the document service; `LocalDocumentsClient` is the stand-in to replace  |
| `docs/loan_approval.png`                                            | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean. Everything specific to the use case belongs into the test
extending `WorkflowModuleTest`, never into the base class.

## Adding this blueprint to an existing project

1. Find out whether the process can hold more than one token: a parallel or inclusive
   gateway, a non-interrupting boundary event, a parallel multi-instance activity, a
   non-interrupting event subprocess. If it can, this blueprint applies. VanillaBP says so
   as well, with a warning at startup naming the elements.
2. Split the workflow aggregate. What a branch writes goes into an entity of that branch,
   related to the aggregate. What stays on the aggregate is what a single token writes:
   before the split, and after the join.
3. Move the id of a task the application answers into the entity of its branch. It is
   written while the branches run, so the aggregate is the wrong place for it.
4. Add a `@WorkflowTask` method per branch, each calling `Service` as everywhere else.
   Neither method may read or write what the other branch owns; if they have to, the split
   in step 2 is wrong.
5. Never put `@Transactional` on the methods a task handler calls. The transaction of a task
   belongs to VanillaBP. The methods the API calls do need one, and that transaction is
   where a retry belongs if you choose a version attribute instead of this data model.
6. Copy `LoanApprovalIT` together with the simulator. A test which does not make the two
   transactions overlap does not test this at all: it passes on a fast machine and hides the
   defect. Order the branches through the application - both of them wait for it - and make
   one of them slow rather than blocking a task handler until the test releases it: a handler
   holds a thread of the BPMS, and on a remote engine one adapter shares few of them.

The other three ways to deal with the collision are in
[the wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate):
`@DynamicUpdate`, a version attribute plus your own retry, and an additive relation. Which
one fits depends on what the branches do. This blueprint shows the first way because it is
the only one that keeps working when the branches grow.

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
