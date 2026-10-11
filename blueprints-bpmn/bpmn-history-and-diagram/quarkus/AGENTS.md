# bpmn-history-and-diagram

Serves the BPMN of a workflow and the elements it has passed. A viewer needs both to draw a
running workflow. A delta on top of `module-single`.

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

|              Name               |                                  Where it occurs                                  |
|---------------------------------|-----------------------------------------------------------------------------------|
| `SendTask_AwaitPartnerApproval` | the BPMN element the workflow waits at and the `@WorkflowTask` method serving it  |
| `historyContext`                | the request parameter of `/definitions` and `/history`, passed through to the SPI |

## Core files

|                               File                                |                                             Why it matters                                             |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`      | `getProcessDefinitions`, `getBpmnXml` and `getWorkflowHistory`, where `ProcessService` is injected     |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | the three GET endpoints a UI calls, and the XML content type of the diagram                            |
| `loan-approval/src/main/java/.../loanapproval/Service.java`       | passes the calls through WITHOUT a transaction, because reading a diagram changes nothing              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`             | asks what a viewer asks, of a running and of an ended workflow, and waits instead of asserting at once |

## Boilerplate files

|                                  File                                   |                                       Purpose                                        |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                                 | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                                   | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                       | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`                     | the database of the module's own test                                                |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | the tasks of the process, including the `@TaskId` one the workflow waits at          |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`     | the workflow aggregate, holding the id of the task waited at                         |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the two numbers the business code uses                                               |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the base class
does not make the test a bean.

## Adding this blueprint to an existing project

1. Put the three calls into the class which already owns `ProcessService`, next to
   `startWorkflow`. They are outgoing calls like every other one, and that class exists for
   them.
2. Do NOT wrap them in a transaction. They read, they store nothing, and they do not move the
   workflow on.
3. Expose them as they are. First the definitions, because a viewer needs an id before it can
   ask for a diagram. Then the XML. Then the history.
4. **Treat a process definition id as opaque.** Never parse, shorten or rebuild it. VanillaBP
   puts the adapter which can resolve it in front of the BPMS' id. A UI which carries it in a
   URL has to hand it back unchanged.
5. Take `historyContext` as an optional parameter on the definitions and the history endpoint,
   even without call activities in the model. It lets a viewer dig into a call activity later.
   Adding it afterwards means changing a published API.
6. Assume that the history LAGS on a remote BPMS (business process management system), and that
   it may have fewer details than you expect. Do not build logic on it. A user looks at it, but
   the application does not decide by it. The application decides by the workflow aggregate.
7. Do not collect the history yourself. A handler which writes "I was here" onto the aggregate
   repeats what the BPMS already knows. It drifts apart from it the first time a workflow is
   cancelled.

If the process has call activities, follow the `secondaryWorkflowHistoryContext` of a call
activity which ran back into `getProcessDefinitions`, to show the called model. See
[`bpmn-call-activity-decomposition`](https://github.com/vanillabp-blueprints/bpmn-call-activity-decomposition-quarkus).

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs
`vanillabp.adapters.camunda8.rest-address` configured and a cluster the adapter can SEARCH:
secondary storage configured, and credentials allowed to read it. On a cluster which refuses a
search the workflow module does not deploy. That is the cluster and not a defect of the
generated code, and the message names which of the two is missing.

`LoanApprovalIT` proves the aspect and has to pass: the definitions of the workflow, the BPMN
behind them, the element where the workflow stands while it waits, and the history of the ended
workflow. Write such a test with a WAIT loop, never with an immediate assertion. A remote BPMS
feeds its history through an exporter. An assertion which passes on an embedded engine would
fail on the next one.

Do not report success without having run this.
