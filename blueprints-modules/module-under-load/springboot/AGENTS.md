# module-under-load

Puts a workflow module under load and names the pool which runs out first, with the setting to
change. A load driver plays customers, clerks and a neighbour system, as a load profile next to
the BPMN files says. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                                                              |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Blueprint-specific names, each occurring in more than one place:

|               Name               |                                         Where it occurs                                          |
|----------------------------------|--------------------------------------------------------------------------------------------------|
| `UserTask_AssessLoan`            | the user task in the BPMN, the load profile and the constant `Clerks.USER_TASK`                  |
| `Gateway_Approved`               | the gateway in the BPMN, the load profile and `Clerks.GATEWAY`                                   |
| `Flow_ToPayoutRun`               | the approved flow in the BPMN, the load profile and `Clerks.APPROVED_FLOW`                       |
| `CatchEvent_CreditScoreReceived` | the message catch event in the BPMN, the load profile and `CreditBureauSimulator.ANSWER_ELEMENT` |
| `CreditScoreReceived`            | the BPMN message name and `Workflow.CREDIT_SCORE_RECEIVED`                                       |
| `payoutRunAt`                    | the timer expression, the aggregate and `declared-aggregate-values` in `loan-approval.yaml`      |

## Core files

|                                            File                                            |                                               Why it matters                                               |
|--------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/load-profile.yaml`                         | the load profile: arrivals, aggregate size, gateway shares, people, neighbour systems, hot path            |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the shapes which cost something: a neighbour system, a message or its timeout, a user task, a shared timer |
| `loan-approval/src/main/java/.../loanapproval/CreditBureau.java`                           | the neighbour system as a port of the module; the answer comes later through `Service.creditScoreReceived` |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | the methods the driver calls, the same ones a browser, a task list or a credit bureau would call           |
| `load-driver/src/main/java/.../loaddriver/LoadRun.java`                                    | the stages, the sampling once a second, the drain                                                          |
| `load-driver/src/main/java/.../loaddriver/Report.java`                                     | the knee, the queue which turned first, and the setting it names                                           |
| `load-driver/src/main/java/.../loaddriver/Queues.java`                                     | the meters read: `vanillabp.task.deliveries`, `vanillabp.outbox.*`, the pool, the execution slots, the JVM |
| `load-driver/src/main/java/.../loaddriver/config/LoadDriverAutoConfiguration.java`         | wires the driver, the simulated credit bureau and the meter name of the pool                               |
| `application/src/main/resources/application.yaml`                                          | the pool, `vanillabp.outbox.dispatch-threads`, `vanillabp.metrics.gauge-cache`, the run sizes              |
| `application/src/test/java/.../LoadRunIT.java`                                             | the proof that a tiny run starts and finishes                                                              |

## Boilerplate files

|                                File                                 |                                                Purpose                                                 |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles and the VanillaBP BOM import                                                         |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support`, never an adapter                                                      |
| `load-driver/pom.xml`                                               | the module, Micrometer and SnakeYAML; no load testing tool                                             |
| `application/pom.xml`                                               | the BPMS adapter, the load driver and the actuator                                                     |
| `application/src/main/java/.../Application.java`                    | the application class, in the parent package of the module                                             |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | walks one loan through in a browser                                                                    |
| `loan-approval/src/main/resources/openapi/loan-approval.yaml`       | the API of the module. The build generates `LoanApprovalApi` from it                                   |
| `load-driver/src/main/resources/openapi/load-run.yaml`              | the two URLs of a run. The build generates `LoadRunApi` from it, and `LoadRunController` implements it |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java` | the shared `isApproved()` and `payoutRunAt`, and the documents which give the aggregate its size       |
| `loan-approval/src/test/java/.../ScriptedCreditBureau.java`         | the credit bureau of the module test, which can stay silent                                            |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`               | each path of the process, one loan at a time                                                           |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                                                        |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                                        |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                                         |
| `docs/loan_approval.png`                                            | the picture of the process the README shows                                                            |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint. Copy them unchanged.

## Adding this blueprint to an existing project

1. Write a load profile for your process, next to its BPMN files, with the keys of
   `load-profile.yaml`. Key every value by the id of the BPMN element it is about. Ask the
   person who knows the business for the values. They are not in the model.
2. Copy the Maven module `load-driver` into the project and add it as a dependency of the
   application. Never add it to a workflow module.
3. Adapt `Customers`, `Clerks` and `CreditBureauSimulator` to the service methods of your
   module and to the ids of your user tasks, gateways and catch events. Keep the rule that the
   driver only calls what a browser, a task list or a neighbour system would call.
4. Give each neighbour system of your module a port like `CreditBureau`, if it has none. The
   driver implements the port. In production, a client of the real system does.
5. Put Micrometer into the application (`spring-boot-starter-actuator`), and set
   `vanillabp.metrics.gauge-cache` to `PT1S`. Without Micrometer, every queue reads as a dash.
6. If the project uses another connection pool, change the meter name and the setting in
   `LoadDriverAutoConfiguration`.
7. Copy `LoadRunIT` with a tiny size. It proves that the driver starts and finishes. Never
   assert a number of a run in a test.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

Two tests have to pass:

- `LoanApprovalIT`: an answered credit score reaches the clerk, a silent bureau ends in an
  assessment without a score, an approved loan is paid out in the next payout run, a rejected
  one gets its rejection,
- `LoadRunIT`: a tiny run walks through every stage, every workflow it started ends, and no
  request or decision fails.

A wrong key in the load profile stops the application at startup, and the message names the
key. Do not report success without having run this.
