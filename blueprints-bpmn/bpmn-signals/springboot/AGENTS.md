# bpmn-signals

Adds a BPMN signal which the application broadcasts. It reaches every workflow of the module
which waits for that name. It carries no data and is not buffered. A delta on top of
`module-single`.

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

|          Name           |                                     Where it occurs                                     |
|-------------------------|-----------------------------------------------------------------------------------------|
| `InterestRatePublished` | the constant `Workflow.INTEREST_RATE_PUBLISHED` and the `bpmn:signal` name in the model |
| `applyInterestRate`     | the `@WorkflowTask` method behind the signal event and the task definition of that task |
| `rate_watch`            | the BPMN process id of the second process and its model file                            |
| `ratewatch`             | the Java package of the second use case, `rate-watch` in its REST path                  |

The signal name is the contract between code and model. If the two drift apart, the broadcast
reaches no waiting event, and nothing happens. There is no exception, because a signal nobody
catches is a valid outcome.

## Core files

|                                            File                                            |                                             Why it matters                                              |
|--------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the signal catch event and the `bpmn:signal` it references                                              |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `sendSignal(signalName)` and the signal name as a constant. No aggregate is passed                      |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | stores the data the signal cannot carry BEFORE broadcasting, and reads it again when a workflow goes on |
| `loan-approval/src/main/java/.../loanapproval/model/InterestRate.java`                     | the data a waiting workflow reads: application data, which belongs to no workflow                       |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | where each workflow puts what it read                                                                   |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the endpoint which triggers the broadcast. On purpose without a business case id in its path            |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | waiting, one broadcast continuing two workflows, and a signal nobody caught                             |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/rate_watch.bpmn`    | a second process of the SAME workflow module, waiting for the same signal                               |
| `loan-approval/src/main/java/.../ratewatch/`                                               | the second use case: own aggregate, own API, no code shared with the loan approval                      |
| `loan-approval/src/test/java/.../RateWatchIT.java`                                         | proves the scope: one use case broadcasts, the process of the other one continues                       |

## Boilerplate files

|                            File                            |                                             Purpose                                             |
|------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                 | the BPMS profiles and the VanillaBP BOM import                                                  |
| `loan-approval/pom.xml`                                    | `vanillabp-spring-boot-support`, never an adapter                                               |
| `application/pom.xml`                                      | the BPMS adapter, the only place a BPMS is named                                                |
| `application/src/main/java/.../Application.java`           | the Spring Boot application, in the parent package of the module                                |
| `application/src/main/resources/application.yaml`          | the datasource, and the profile the Maven build filters in                                      |
| `application/src/main/resources/application-camunda7.yaml` | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications |
| `loan-approval/src/test/java/.../TestApplication.java`     | the minimal application the module's test boots                                                 |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`  | base class of the integration test: waits for workflow progress                                 |
| `application/src/test/java/.../ApplicationSmokeTest.java`  | boots the application, which validates the BPMN-to-code wiring                                  |
| `docs/loan_approval.png`                                   | the picture of the process the README shows, rendered from the BPMN model                       |

## Adding this blueprint to an existing project

1. Add the signal catch event to the BPMN and declare a `bpmn:signal` for it. There is nothing
   else to model. A signal is a broadcast, so no correlation, no key and no expression appears
   anywhere.
2. Decide where to keep the data which the waiting workflows will need. **A signal transports
   nothing**, not even by way of an aggregate, because the sender has none. The answer is
   ordinary application data, which the task behind the catch event reads.
3. Add a method to `Service` which stores that data and only then calls `Workflow`. Annotate it
   with `@Transactional`. On a remote BPMS (business process management system) the broadcast
   is sent after the commit. So the data is in place before any workflow reads it.
4. Add the `sendSignal` call to `Workflow`, and keep the signal name there as a constant. The
   method takes no workflow aggregate. Giving it one would be a design error, not a missing
   parameter.
5. Add the endpoint which triggers the broadcast. Give it no business case id. Nothing about a
   signal is addressed, and a path which suggests otherwise misleads whoever reads the API.
6. When a workflow starts waiting, log the URL which continues the process. Say in the log that
   it continues every waiting workflow at once.
7. Copy `LoanApprovalIT`: one test for the waiting workflow, one for a single broadcast which
   continues two of them, one for a signal nobody caught.
8. If more than one process of the module should react, model the same signal in each of them,
   and send nothing extra. A broadcast covers the whole workflow module. So `rate_watch` here
   needs no sender of its own.

A second use case in one workflow module brings name clashes, and the platform will not resolve
them for you. This blueprint shows the answers. The entity gets a name of its own
(`@Entity(name = "RateWatch")`). So do the beans whose classes the reference structure calls
`Service`, `Workflow`, `ApiController` and `WorkflowTaskHandler` in every use case. Spring Data
names a repository bean after its interface, and that needs the same treatment.

Sending the same signal in several workflow MODULES is a different matter. It means calling
`sendSignal` on the `ProcessService` of each module. A broadcast is limited to one workflow
module. Which modules are meant is a business decision, and VanillaBP does not take it.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- before the broadcast, the service task behind the catch event has NOT run. This proves that
  the workflow waits,
- one broadcast continues two workflows nobody addressed, and both read the published value,
- a workflow which reaches the catch event after a broadcast stays there until the next one.

`RateWatchIT` proves the scope and has to pass as well. The second process of the module goes
on, although the other use case sent the broadcast.

**A test must broadcast again and again, not once.** A signal is not buffered. Progress on the
workflow aggregate does not prove that the subscription of the catch event exists yet. On a
remote engine those are two transactions. Sending in a poll loop until the workflows reacted is
the honest way to wait for it. A repeat costs nothing, because a signal nobody waits for
reaches nobody.

Do not report success without having run this.
