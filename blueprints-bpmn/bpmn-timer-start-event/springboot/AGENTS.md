# bpmn-timer-start-event

Adds a process with two start events which the engine fires on its own: a timer start event
and a signal start event. Each one has its own `@WorkflowStartedByBpms(id = ...)` method,
which builds a different workflow aggregate. Nobody calls `startWorkflow`. A delta on top of
`module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                        Placeholder                         |                                                                                 Meaning                                                                                 |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                                 | base package                                                                                                                                                            |
| `blueprint.application`                                    | package of the application, not above the workflow module                                                                                                               |
| `loanapproval`                                             | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                                            | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                             | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                            | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.bpmn-timer-start-event-springboot` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `bpmn-timer-start-event`                                   | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|             Name             |                                                                  Where it occurs                                                                  |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `portfolio_review`           | the BPMN process id of the second process and its model file                                                                                      |
| `portfolioreview`            | the Java package of the second use case, below `loanapproval` because it belongs to the same workflow module. `portfolio-review` in its REST path |
| `StartEvent_ScheduledReview` | the BPMN id of the timer start event and the constant `WorkflowTaskHandler.SCHEDULED_REVIEW`, which `@WorkflowStartedByBpms(id = ...)` names      |
| `StartEvent_ReviewRequested` | the BPMN id of the signal start event and the constant `WorkflowTaskHandler.REVIEW_REQUESTED`, which `@WorkflowStartedByBpms(id = ...)` names     |
| `PortfolioReviewRequested`   | the constant `Workflow.REVIEW_REQUESTED` and the `bpmn:signal` name in the model                                                                  |
| `reviewPortfolio`            | the `@WorkflowTask` method and the task definition of the service task                                                                            |
| `SCHEDULED`, `REQUESTED`     | the values of `Occasion`, one per start event. The test waits for each of them                                                                    |

## Core files

|                                             File                                              |                                           Why it matters                                            |
|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/portfolio_review.bpmn` | the timer start event and the signal start event. No call triggers either of them                   |
| `loan-approval/src/main/java/.../portfolioreview/WorkflowTaskHandler.java`                    | two `@WorkflowStartedByBpms(id = ...)` methods, one per start event, each returning the aggregate   |
| `loan-approval/src/main/java/.../portfolioreview/Service.java`                                | builds a different aggregate per start event and names it. It has NO method which starts a workflow |
| `loan-approval/src/main/java/.../portfolioreview/model/Aggregate.java`                        | the aggregate, with the occasion the building method writes                                         |
| `loan-approval/src/main/java/.../portfolioreview/model/Occasion.java`                         | one value per start event                                                                           |
| `loan-approval/src/test/java/.../PortfolioReviewIT.java`                                      | waits for an aggregate of each occasion, because there is nothing it could call                     |

## Boilerplate files

|                                    File                                     |                                                                            Purpose                                                                             |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                  | the BPMS profiles and the VanillaBP BOM import                                                                                                                 |
| `loan-approval/pom.xml`                                                     | `vanillabp-spring-boot-support`, never an adapter                                                                                                              |
| `application/pom.xml`                                                       | the BPMS adapter, the only place a BPMS is named                                                                                                               |
| `application/src/main/java/.../Application.java`                            | the Spring Boot application, in `blueprint.application`, which is not above the module. It names nothing of the module                                         |
| `loan-approval/src/main/java/.../config/LoanApprovalAutoConfiguration.java` | the module wires itself: a component scan of its own package, registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `application/src/main/resources/application.yaml`                           | the datasource, and the profile the Maven build filters in                                                                                                     |
| `application/src/main/resources/application-camunda7.yaml`                  | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications                                                                |
| `loan-approval/src/test/java/.../TestApplication.java`                      | the minimal application the module's test boots. It scans nothing and gets the module from its auto-configuration                                              |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                   | base class of the integration test: waits for workflow progress                                                                                                |
| `application/src/test/java/.../ApplicationSmokeTest.java`                   | boots the application, which validates the BPMN-to-code wiring                                                                                                 |
| `docs/portfolio_review.png`                                                 | the picture of the process the README shows, rendered from the BPMN model                                                                                      |

## Adding this blueprint to an existing project

1. Model the start events. A timer start event takes a cycle (`bpmn:timeCycle`, for example
   `R/P1D`) or a date (`bpmn:timeDate`), written as a literal: no aggregate exists before the
   timer fires, so an expression would have nothing to read. A cron expression in the cycle
   runs in a time zone the BPMS chooses, never in the zone of the business. Never set the time
   zone of the machine to make it fit. A time of day in the zone of the business belongs to a
   timer inside a workflow, see `bpmn-timer`. A MESSAGE start event is
   something else. `startWorkflowByMessage` triggers that one, and it belongs to
   `bpmn-message-start`.
2. Add one `@WorkflowStartedByBpms(id = "...")` method per start event the BPMS fires on its
   own, to the workflow service. `id` is the BPMN id of the start event. Keep the ids as
   constants of the handler. Each method **builds** the workflow aggregate and returns it. It
   may take a `BpmsStartTrigger` and `@TaskParam` process variables in any order, and it needs
   neither to know which event fired. It runs in the transaction of the start, so if it
   throws, the workflow does not start.
3. **Every such start event needs a method.** The application does not boot while one is
   missing, or while an `id` names a start event the process does not have. Do not add a
   method without `id` next to these. It would serve every start event, and VanillaBP would
   call the first method it finds.
4. **Assign the ID in each method, and add no method which starts the workflow.** The ID of a
   workflow is the ID of its aggregate, and only the application picks it.
5. Wire the rest of the process as usual. Behind the start events nothing is special any more.
6. Copy `PortfolioReviewIT`. It waits for an aggregate of each occasion instead of starting
   one.

If all start events lead to the same aggregate, one method without `id` is simpler. That is
what `bpmn-bpms-initiated-start` shows.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

The Process-Engine-API adapter cannot run a process like this. It fails the deployment with a
message which names the element. That is the correct behaviour, not a defect of the blueprint.

`PortfolioReviewIT` proves the aspect and has to pass:

- an aggregate with the occasion `SCHEDULED` appears without any call. Only the method for the
  timer start event writes that value, and it writes no signal name,
- an aggregate with the occasion `REQUESTED` appears after the signal was broadcast. Only the
  method for the signal start event writes that value, together with the name of the signal,
- the service task behind each of them ran, so the workflow went on as an ordinary one.

A test for a start which the BPMS triggers has to WAIT, not act. No call starts the workflow,
and a timer fires when the engine gets to it. Do not assert a fixed number of runs of a cyclic
timer either. How many fire within a test differs between engines.

Do not report success without having run this.
