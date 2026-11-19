# bpmn-timer

Adds waiting to the process: a timer in the sequence flow which delays it, and an interrupting
timer boundary event which ends a wait nobody answered. The first timer reads a getter of the
aggregate, the second an attribute copied from configuration. A change made while the workflow
waits reaches the BPMS through `aggregateChanged`. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                 Placeholder                 |                                                                                 Meaning                                                                                 |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                  | base package                                                                                                                                                            |
| `loanapproval`                              | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                             | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                             | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                              | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                             | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.bpmn-timer-quarkus` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `bpmn-timer`                                | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|           Name           |                                                  Where it occurs                                                  |
|--------------------------|-------------------------------------------------------------------------------------------------------------------|
| `requestPartnerApproval` | the `@WorkflowTask` method, the Camunda 7 `camunda:delegateExpression` and the Camunda 8 `zeebe:taskDefinition`   |
| `informCustomer`         | the `@WorkflowTask` method behind the waiting task and the task definition of that service task                   |
| `noteTimeout`            | the `@WorkflowTask` method on the path behind the timer and the task definition of that service task              |
| `confirmWithdrawal`      | the `@WorkflowTask` method on the withdrawn branch and the task definition of that service task                   |
| `partnerDeadline`        | the aggregate attribute, the expression of the timer boundary event, and the configuration key `partner-deadline` |
| `withdrawn`              | the aggregate attribute and the condition of the gateway behind the cool-off timer                                |
| `coolOffPeriod`          | the getter `getCoolOffPeriod` of the aggregate and the expression of the cool-off timer                           |

Neither timer has a literal in the model. The cool-off duration is worked out by the getter
`getCoolOffPeriod` from the amount: twice as long for a loan of `LARGE_LOAN` or more. The
deadline on the waiting task is `partner-deadline` in `loan-approval.yaml`, copied into the
aggregate when the loan is requested. Both timers read their value with an expression.

## Core files

|                                            File                                            |                                                                                      Why it matters                                                                                      |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the timer catch event, the gateway behind it, the waiting task and the INTERRUPTING timer boundary event on it                                                                           |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                    | the `@WorkflowTask` method of that task: aggregate, `@TaskId`, `@TaskEvent`. Returning does NOT complete the task                                                                        |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `aggregateChanged` after a withdrawal, because no sync point comes while the timer waits; `completeTask`; nothing cancels the task from the application here, the timer does that        |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | takes a withdrawal, sends the request once, keeps the task id, answers the task when the surrounding system replies                                                                      |
| `loan-approval/src/main/java/.../loanapproval/PartnerApprovalClient.java`                  | the port to the surrounding system, so a test can put a simulator in its place                                                                                                           |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the withdrawal, and the callback the answer arrives at, carrying the task id                                                                                                             |
| `loan-approval/src/main/resources/loan-approval/openapi/loan-approval.yaml`                | the API as an OpenAPI file. The build generates the interface `LoanApprovalApi` from it, and `ApiController` implements it                                                               |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | `partnerApprovalTaskId`, the attributes the service tasks write, and `partnerDeadline`, `withdrawn` and the getter `getCoolOffPeriod` with `@SyncWithBPMS`, because the model reads them |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`                        | `partner-deadline: P3D`, the business value of the deadline                                                                                                                              |
| `loan-approval/src/test/resources/application.yaml`                                        | `partner-deadline: PT3S`, which keeps the test of the deadline fast                                                                                                                      |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | one test per way the workflow ends: answered in time, taken away by the deadline, withdrawn during the cool-off period; and one which proves the cool-off timer reads the getter         |

## Boilerplate files

|                                      File                                      |                                              Purpose                                              |
|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                     | the BPMS profiles, the Quarkus BOM and the VanillaBP BOM import                                   |
| `loan-approval/pom.xml`                                                        | `vanillabp-quarkus-support` and the index of the module's classes, never an adapter               |
| `application/pom.xml`                                                          | `vanillabp-quarkus-integration` and the BPMS adapter, the only place a BPMS is named              |
| `application/src/main/resources/application.yaml`                              | the database, and the short deadline of the demo                                                  |
| `loan-approval/src/main/java/.../loanapproval/LocalPartnerApprovalClient.java` | stand-in for the surrounding system, so the blueprint runs alone. Replace it with the real client |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                      | base class of the integration test: waits for workflow progress                                   |
| `loan-approval/src/test/java/.../Simulator.java`                               | base class of a stand-in for a surrounding system                                                 |
| `application/src/test/java/.../ApplicationSmokeTest.java`                      | boots the application, which validates the BPMN-to-code wiring                                    |
| `docs/loan_approval.png`                                                       | the picture of the process the README shows, rendered from the BPMN model                         |

`WorkflowModuleTest`, `Simulator` and `ApplicationSmokeTest` are identical in every blueprint.
Copy them unchanged. Every test class carries `@QuarkusTest` itself. Inheriting it from the
base class does not make the test a bean.

## Adding this blueprint to an existing project

1. Model the waiting. A timer catch event in the sequence flow delays the process. An
   interrupting timer boundary event on a task ends a wait. A task which waits has to be able
   to stay open. On Camunda 7 that is `camunda:delegateExpression`, on Camunda 8 a plain
   `zeebe:taskDefinition`.
2. Add an attribute for the task id to the workflow aggregate. Without it nobody can answer the
   task later, because its id is the only handle to it.
3. Add an interface for the surrounding system, and the real client as its implementation.
   Do not call the system from `WorkflowTaskHandler`.
4. Add the `@WorkflowTask` method named after the task definition. It takes the aggregate,
   `@TaskId` for the id, and `@TaskEvent` to tell delivery from cancellation. It calls
   `Service` for each of the two. Never complete the task there.
5. Add the business methods to `Service`. The first returns early if the request went out
   already, and otherwise sends it and stores the id. The second drops the id when the task was
   canceled. Then add one per answer the API accepts. Put `@Transactional` on the class
   `Service`, so every method runs in a transaction. The ones the task handler calls join the
   transaction of the task.
6. Let the answering method reject a task id which is not the one stored on the aggregate. An
   answer arrives from outside and may come after its task is gone.
7. Add a `completeTask` call to `Workflow`, one method per answer. Nothing in the application
   cancels the task. The timer does that.
8. Put a deadline which belongs to the business into the configuration of the module, not into
   the model. Copy it into an attribute of the aggregate when the workflow starts, mark that
   attribute `@SyncWithBPMS`, and let the timer read it: `${partnerDeadline}` on Camunda 7,
   `=partnerDeadline` on Camunda 8. Use a `String`, so `P3D` stays `P3D`. A fixed period of
   the process may stay a literal. A model you received may carry a business deadline as a
   literal, such as `P3D` on a timer boundary event. Turn it into an expression in the same
   way, and keep its value in the configuration. That is part of making the model run with
   VanillaBP, as adding the task definitions is. It is not a change of the model for a test.
   If the duration follows a rule of the business case, write the rule as a getter of the
   aggregate which returns the ISO-8601 text, mark the getter `@SyncWithBPMS`, and let the
   timer read its name: `${coolOffPeriod}` on Camunda 7, `=coolOffPeriod` on Camunda 8.
   VanillaBP calls the getter at every sync point, and the timer reads the value of the last
   one. So never count from the current time in such a getter. Count from a point in time the
   aggregate stores.
9. Add the callback endpoint which carries the task id. When the request goes out, log the URLs
   which continue the process.
10. If the business case may change while the workflow waits for a timer, and the model reads
    the change behind the timer, call `aggregateChanged` in `Workflow` after the change. Mark
    the attribute `@SyncWithBPMS`. Without the call the BPMS keeps the old value until the next
    time VanillaBP talks to it.
11. Copy `LoanApprovalIT` and write one test per way the task ends. Shorten the deadline in the
    test's `application.yaml`, as this blueprint does. Never change the model for a test.

Do not build waiting yourself. A scheduler in the application repeats what the BPMS (business
process management system) already stores. It loses its state on every restart, and a timer
does not.

Never rely on the cancellation reaching your handler, because not every BPMS reports it.
Answering a task that is gone does nothing. So your code has to be correct without hearing
about it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- an answer before the deadline completes the task: the service task behind it has run, and
  the one behind the timer has not,
- without an answer the deadline takes the task away: the service task behind the timer has
  run, the one behind the answer has not, the aggregate holds the shortened deadline `PT3S`, and
  on Camunda 7 the stored id is gone,
- a withdrawal during the cool-off period reaches the gateway behind it: the service task on the
  withdrawn branch has run, and the partner was never asked,
- a large loan waits for the cool-off period its getter works out: the partner is asked no
  earlier than `PT20S` after the request.

If the task is completed as soon as the handler returns, the model wires it in a way that does
not let it stay open. On Camunda 7 that is `camunda:expression`. If the second test never sees
the path behind the timer, the timer boundary event is missing from the waiting task. If the
third test sees the partner asked, `Workflow` does not call `aggregateChanged`, or `withdrawn`
is not shared with the BPMS. If the fourth test sees the partner asked too early, the cool-off
timer does not read `coolOffPeriod`. If Camunda 7 warns at startup that an expression reads
an attribute which is not shared with the BPMS, the getter lacks `@SyncWithBPMS`.

Do not report success without having run this.
