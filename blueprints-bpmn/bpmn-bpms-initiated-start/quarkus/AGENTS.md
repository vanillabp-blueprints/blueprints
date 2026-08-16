# bpmn-bpms-initiated-start

Adds a process the engine starts on its own, by a timer or by a broadcast signal. Nobody
calls `startWorkflow`, so VanillaBP builds the workflow aggregate on the way in. A delta on
top of `module-single`.

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

|                            Name                            |                                                  Where it occurs                                                   |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `nightly_review`                                           | the BPMN process id of the second process and its model file                                                       |
| `nightlyreview`                                            | the Java package of the second use case, `nightly-review` in its REST path                                         |
| `ReviewRequested`                                          | the constant `Workflow.REVIEW_REQUESTED` and the `bpmn:signal` name in the model                                   |
| `reviewPendingApprovals`                                   | the `@WorkflowTask` method and the task definition of the service task                                             |
| `StartEvent_ScheduledReview`, `StartEvent_ReviewRequested` | the BPMN ids of the two start events; the test asserts them and `@WorkflowStartedByBpms(id = ...)` would name them |

## Core files

|                                            File                                             |                                      Why it matters                                       |
|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/nightly_review.bpmn` | the timer start event and the signal start event; neither is triggered by a call          |
| `loan-approval/src/main/java/.../nightlyreview/WorkflowTaskHandler.java`                    | `@WorkflowStartedByBpms` taking the aggregate and a `BpmsStartTrigger`                    |
| `loan-approval/src/main/java/.../nightlyreview/Service.java`                                | writes what the trigger carried; has NO method starting a workflow                        |
| `loan-approval/src/main/java/.../nightlyreview/Workflow.java`                               | `sendSignal` for asking, and deliberately no `startWorkflow`                              |
| `loan-approval/src/main/java/.../nightlyreview/model/Aggregate.java`                        | an aggregate nobody constructs: needs a no-argument constructor, ID assigned by VanillaBP |
| `loan-approval/src/test/java/.../NightlyReviewIT.java`                                      | waits for an aggregate to appear, because there is nothing it could call                  |

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
| `docs/nightly_review.png`                                 | the picture of the process the README shows, rendered from the BPMN model            |

## Adding this blueprint to an existing project

1. Model the start event. A timer (`bpmn:timeCycle`, `bpmn:timeDate`) or a signal start
   event; a conditional one where the BPMS supports it. A MESSAGE start event is something
   else entirely - that one is triggered by `startWorkflowByMessage` and belongs to
   `bpmn-message-start`.
2. Give the workflow aggregate a constructor without arguments and expect every attribute to
   be null while it is being built. VanillaBP instantiates the class before any of your code
   runs.
3. **Do not assign the ID yourself and do not add a method starting the workflow.** VanillaBP
   assigns the most stable identity the BPMS offers: the engine's own identity for the start,
   otherwise a timer's trigger time, otherwise a generated value. A `startWorkflow` call for
   such a process would create a second, unrelated workflow.
4. Add a `@WorkflowStartedByBpms` method to the workflow service if the aggregate needs more
   than VanillaBP fills in. It is optional. The method may take the aggregate, a
   `BpmsStartTrigger` and `@TaskParam` process variables in any order, and it runs in the
   transaction of the start: throwing means the workflow does not start.
5. Name the start event with `@WorkflowStartedByBpms(id = "...")` when the process has more
   than one and they need different code. Without an id the method serves every
   BPMS-initiated start event of its process.
6. Wire the rest of the process as usual. Behind the start event nothing is special any more.
7. Copy `NightlyReviewIT`: it waits for an aggregate to appear instead of starting one, and
   it asserts what the trigger carried.

Process variables the model sets are copied into equally named attributes of the aggregate.
That is the only data a BPMS-initiated start brings along - there is no caller who could
hand anything over.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

The Process-Engine-API adapter cannot run a process like this and fails the deployment with
a message naming the element. That is the correct behaviour, not a defect of the blueprint.

`NightlyReviewIT` proves the aspect and has to pass:

- an aggregate started by the TIMER appears without any call, carrying the trigger time and
  the id of the start event,
- the service task behind it ran, so the workflow continued as an ordinary one,
- broadcasting the signal produces an aggregate started by the SIGNAL.

A test for a BPMS-initiated start has to WAIT rather than act: there is no call that starts
the workflow, and a timer fires when the engine gets to it. Do not assert on a fixed number
of runs of a cyclic timer either - how many fire within a test differs between engines.

Do not report success without having run this.
