# bpmn-history-and-diagram

Serves the BPMN of a workflow and the elements it has passed, which is what a viewer needs to
draw a running workflow. A delta on top of `module-single`.

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

|              Name               |                                  Where it occurs                                  |
|---------------------------------|-----------------------------------------------------------------------------------|
| `SendTask_AwaitPartnerApproval` | the BPMN element the workflow waits at and the `@WorkflowTask` method serving it  |
| `historyContext`                | the request parameter of `/definitions` and `/history`, passed through to the SPI |

## Core files

|                               File                                |                                             Why it matters                                              |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`      | `getProcessDefinitions`, `getBpmnXml` and `getWorkflowHistory`, where `ProcessService` is injected      |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java` | the three GET endpoints a UI calls, and the XML content type of the diagram                             |
| `loan-approval/src/main/java/.../loanapproval/Service.java`       | passes the calls through WITHOUT a transaction - reading a diagram changes nothing                      |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`             | asks what a viewer asks, of a running and of an ended workflow, and waits rather than asserting at once |

## Boilerplate files

|                                  File                                   |                                       Purpose                                        |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                              | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                                 | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                                   | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`                       | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`                     | the database of the module's own test, and where that test reads its BPMN from       |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java` | the tasks of the process, including the `@TaskId` one the workflow waits at          |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`     | the workflow aggregate, holding the id of the task waited at                         |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`     | the two numbers the business code uses                                               |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`               | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java`               | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/loan_approval.png`                                                | the picture of the process the README shows, rendered from the BPMN model            |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy
them unchanged. Every test class carries `@QuarkusTest` itself; inheriting it from the
base class is not enough to make the test a bean.

## Adding this blueprint to an existing project

1. Put the three calls into the class which already owns `ProcessService`, next to
   `startWorkflow`. They are outgoing calls like every other one, and they are the reason
   that class exists.
2. Do NOT wrap them in a transaction. They read, they persist nothing and they do not advance
   the workflow.
3. Expose them as they are: the definitions first, because a viewer needs an id before it can
   ask for a diagram; then the XML; then the history.
4. **Treat a process definition id as opaque.** Never parse, shorten or rebuild it. VanillaBP
   prefixes the BPMS' id with the adapter which can resolve it, and a UI carrying it in a URL
   has to hand it back unchanged.
5. Take `historyContext` as an optional parameter on the definitions and the history endpoint,
   even without call activities in the model. It is what lets a viewer dig into a call
   activity later, and adding it afterwards means changing a published API.
6. Assume the history LAGS on a remote BPMS and may be less detailed than you expect. Do not
   build logic on it: it is what a user looks at, not what the application decides by. What
   the application decides by is the workflow aggregate.
7. Do not collect the history yourself. A handler writing "I was here" onto the aggregate
   duplicates what the BPMS already knows and drifts from it the first time a workflow is
   cancelled.

If the process has call activities, follow the `secondaryWorkflowHistoryContext` of an executed
call activity back into `getProcessDefinitions` to show the called model - see
[`bpmn-call-activity-decomposition`](https://github.com/vanillabp-blueprints/bpmn-call-activity-decomposition-quarkus).

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster WITH secondary storage and `vanillabp.adapters.camunda8.rest-address`
configured; without secondary storage that adapter reports no element history, which is
documented behaviour and not a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass: the definitions of the workflow, the BPMN
behind them, the element the workflow stands at while it waits, and the history of the ended
workflow. Write such a test with a WAIT loop, never with an immediate assertion - a remote
BPMS feeds its history through an exporter, and an assertion which passes on an embedded
engine would fail on the next one.

Do not report success without having run this.
