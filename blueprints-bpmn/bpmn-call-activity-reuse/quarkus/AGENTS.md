# bpmn-call-activity-reuse

Adds a process which other processes reuse. The reused process has a workflow aggregate and a
workflow service of its own. A calling process starts it from a service task, shows it as a
collapsed pool, and waits at a message event for the result. Two processes use it here, a loan
approval and an overdraft request. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                        Placeholder                        |                                                                                 Meaning                                                                                 |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                                | base package                                                                                                                                                            |
| `loanapproval`                                            | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                           | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                                           | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                            | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                           | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.bpmn-call-activity-reuse-quarkus` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `bpmn-call-activity-reuse`                                | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|            Name            |                                                  Where it occurs                                                  |
|----------------------------|-------------------------------------------------------------------------------------------------------------------|
| `credit_check`             | the process id of the reused process, its BPMN file, and the `@BpmnProcess` of `creditcheck/WorkflowTaskHandler`  |
| `overdraft_request`        | the process id of the second caller, its BPMN file, and the `@BpmnProcess` of `overdraft/WorkflowTaskHandler`     |
| `creditcheck`, `overdraft` | the Java packages of the reused process and of the second caller                                                  |
| `CreditChecked`            | the message both callers wait for: `Workflow.CREDIT_CHECKED` and the `bpmn:message` name. Also the event's record |
| `AgencyAnswered`           | the message the credit check waits for: `creditcheck/Workflow.AGENCY_ANSWERED` and the `bpmn:message` name        |
| `requestCreditCheck`       | the `@WorkflowTask` method of each caller which starts the credit check, and the task definition in its model     |
| `CREDIT_CHECK_REQUESTER`   | the constant in each caller's `Service` which names it in a request. Its listener compares the result with it     |

A message name is the contract between code and model. If the two drift apart, the correlation
reaches no waiting event. The call then fails at runtime, not at startup.

## Core files

|                                            File                                            |                                                Why it matters                                                |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the caller: a service task, the collapsed pool `Credit check`, message flows, and the message event          |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/credit_check.bpmn`  | the reused process. NOTHING in it names a caller                                                             |
| `loan-approval/src/main/java/.../loanapproval/creditcheck/CreditChecks.java`               | how a caller asks for a credit check. The only call a caller makes into the reused process                   |
| `loan-approval/src/main/java/.../loanapproval/creditcheck/CreditChecked.java`              | the event which carries the result back, with who asked and for which case                                   |
| `loan-approval/src/main/java/.../loanapproval/creditcheck/Service.java`                    | starts the reused workflow, takes the answer of the agency, publishes the result                             |
| `loan-approval/src/main/java/.../loanapproval/creditcheck/model/Aggregate.java`            | the aggregate of the reused process. It has an entity name of its own                                        |
| `loan-approval/src/main/java/.../loanapproval/CreditCheckedListener.java`                  | the caller takes its result and ignores the results of others                                                |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | asks for the credit check, writes the score onto the aggregate BEFORE correlating, returns early on a repeat |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `correlateMessage` for the result                                                                            |
| `loan-approval/src/main/java/.../loanapproval/overdraft/...`                               | the second caller, built the same way                                                                        |
| `loan-approval/src/main/resources/loan-approval/openapi/credit-check.yaml`                 | the URL at which the agency answers. The build generates `CreditCheckApi` from it                            |
| `loan-approval/src/test/java/.../creditcheck/CreditCheckIT.java`                           | two callers at the same time get their own results                                                           |

## Boilerplate files

|                           File                            |                                       Purpose                                        |
|-----------------------------------------------------------|--------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                      |
| `loan-approval/pom.xml`                                   | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter  |
| `application/pom.xml`                                     | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named |
| `application/src/main/resources/application.yaml`         | the database, and nothing about the workflow                                         |
| `loan-approval/src/test/resources/application.yaml`       | the database of the module's own test                                                |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java` | base class of the integration test: waits for workflow progress                      |
| `application/src/test/java/.../ApplicationSmokeTest.java` | boots the application, which validates the BPMN-to-code wiring                       |
| `docs/*.png`                                              | the pictures of the three processes the README shows, rendered from the BPMN models  |

## Adding this blueprint to an existing project

1. Give the reused process a package of its own, with its own `Service`, `Workflow`,
   `WorkflowTaskHandler`, `ApiController` and `model/Aggregate`. Its aggregate is a new entity.
   Give it an entity name of its own when another class of the module is called `Aggregate`.
2. Store on that aggregate who asked and for which case, as plain values. Do NOT reference the
   aggregate of a caller. The reused process must not know its callers.
3. Write an interface for the request, like `CreditChecks`, and let the `Service` of the
   reused process implement it. It builds the aggregate and calls `startWorkflow`.
4. Model the end of the reused process with a service task which reports the result. Its
   handler publishes an event, like `CreditChecked`, which carries who asked, the case and the
   result. Do not call a caller back.
5. In each calling model, add a service task which asks for the reused process, a collapsed
   pool for it, and a message catch event for the result. Do NOT use a call activity. A call
   activity would run the reused process on the caller's aggregate.
6. In each caller, add a listener for the event. It ignores the results other callers asked
   for. It hands the result to the caller's `Service`, which writes it onto the caller's
   aggregate and only then correlates the message. Return early when the result is there
   already.
7. Make sure the caller waits at its message event before the result can come. Here the
   reused process has a wait state of its own. If yours has none, it may answer within the
   transaction of the task which started it, and then nothing waits for the message yet. Set
   `message-time-to-live` for the workflow module in that case, see the
   [wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Message-correlation#a-message-which-comes-before-the-workflow-waits-for-it).
8. Copy the three tests: each caller waits and goes on, and two callers at the same time get
   their own results.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT`, `OverdraftRequestIT` and `CreditCheckIT` prove the aspect and have to pass:

- while the credit check waits for the agency, the caller has no score and no decision. This
  proves that the caller waits,
- after the agency answers, the caller has the score on its own aggregate and goes on,
- two callers at the same time each get their own score back,
- an answer which arrives twice changes nothing.

A test which lets the agency answer right after the start has to accept that a remote engine
does not know the credit check yet. It answers such lookups from an index it fills in the
background. That is why the tests retry. An embedded engine does not need the retry.

Do not report success without having run this.
