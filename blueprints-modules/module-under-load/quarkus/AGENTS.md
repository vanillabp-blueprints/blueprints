# module-under-load

Puts a workflow module under load and names the pool which runs out first, with the setting to
change. A load driver plays customers, clerks and a neighbour system, as a load profile next to
the BPMN files says. The driver runs in a simulator, an application of its own, and
reaches the application only over HTTP and JMX. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                    Placeholder                     |                                                                                 Meaning                                                                                 |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                         | base package                                                                                                                                                            |
| `loanapproval`                                     | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                    | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                                    | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                     | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                    | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.module-under-load-quarkus` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `module-under-load`                                | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|               Name               |                                           Where it occurs                                            |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `UserTask_AssessLoan`            | the user task in the BPMN, the load profile and the constant `Clerks.USER_TASK`                      |
| `Gateway_Approved`               | the gateway in the BPMN, the load profile and `Clerks.GATEWAY`                                       |
| `Flow_ToPayoutRun`               | the approved flow in the BPMN, the load profile and `Clerks.APPROVED_FLOW`                           |
| `CatchEvent_CreditScoreReceived` | the message catch event in the BPMN, the load profile and `CreditBureauApiController.ANSWER_ELEMENT` |
| `CreditScoreReceived`            | the BPMN message name and `Workflow.CREDIT_SCORE_RECEIVED`                                           |
| `payoutRunAt`                    | the timer expression, the aggregate and `declared-aggregate-values` in `loan-approval.yaml`          |

## Core files

|                                               File                                                |                                                Why it matters                                                 |
|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/load-profile.yaml`                                | the load profile: arrivals, aggregate size, gateway shares, people, neighbour systems, hot path               |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn`        | the shapes which cost something: a neighbour system, a message or its timeout, a user task, a shared timer    |
| `loan-approval/src/main/java/.../loanapproval/CreditBureau.java`                                  | the neighbour system as a port of the module; the answer comes later through `Service.creditScoreReceived`    |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                                 | the API the driver calls: a request with documents, the task list, the decision, the number of open approvals |
| `loan-approval-api/loan-approval.yaml`                                                            | the API of the module; `server` generates what `ApiController` implements, `client` what the simulator calls  |
| `external-systems/credit-bureau/credit-bureau-api/credit-bureau.yaml`                             | the API of the credit bureau: a question which is answered later                                              |
| `external-systems/credit-bureau/credit-bureau-api/credit-bureau-callback.yaml`                    | the callback the application offers for the answer                                                            |
| `external-systems/credit-bureau/rest-adapter/src/main/java/.../rest/CreditBureauRestAdapter.java` | implements the port with the generated client                                                                 |
| `external-systems/credit-bureau/rest-adapter/src/main/java/.../rest/CallbackController.java`      | takes the score and hands it to `Service.creditScoreReceived`                                                 |
| `simulator/src/main/java/.../loadrun/LoadRun.java`                                                | the stages, the sampling once a second, the drain                                                             |
| `simulator/src/main/java/.../loadrun/Report.java`                                                 | the knee, the queue which turned first, and the setting it names                                              |
| `simulator/src/main/java/.../loadrun/Queues.java`                                                 | the meters read in the Prometheus format: `vanillabp_task_deliveries`, the outbox, the pool, the slots, GC    |
| `simulator/src/main/java/.../loadrun/FullCollection.java`                                         | the full garbage collection of the application, asked for over JMX                                            |
| `simulator/src/main/java/.../creditbureau/CreditBureauApiController.java`                         | plays the credit bureau as the load profile says                                                              |
| `simulator/src/main/resources/application.yaml`                                                   | the run sizes, the address, meters URL and JMX address of the application, the meter name of its pool         |
| `application/src/main/resources/application.yaml`                                                 | the pool, `vanillabp.outbox.dispatch-threads`, `vanillabp.metrics.gauge-cache`, the meters, the bureau        |
| `application/src/test/java/.../LoadRunIT.java`                                                    | the proof that a tiny run against the application starts and finishes                                         |

## Boilerplate files

|                                File                                 |                                                  Purpose                                                   |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the modules, the shared settings of the OpenAPI generator, the VanillaBP BOM import                        |
| `loan-approval/pom.xml`                                             | `vanillabp-quarkus-support` and the generated server interface, never an adapter                           |
| `loan-approval-api/pom.xml`                                         | the API module with its `client` and `server`                                                              |
| `load-run-api/pom.xml`                                              | the API module of the load run with its `client` and `server`                                              |
| `external-systems/credit-bureau/credit-bureau-api/pom.xml`          | the API module of the credit bureau with its `client` and `server`                                         |
| `external-systems/credit-bureau/rest-adapter/pom.xml`               | the module, the generated client and the REST client of Quarkus                                            |
| `simulator/pom.xml`                                                 | a parent of its own, the generated clients and interfaces, SnakeYAML, the load profile copied in           |
| `application/pom.xml`                                               | the BPMS adapter, the REST adapter, the Prometheus registry, JMX for `quarkus:dev`, starting the simulator |
| `simulator/src/main/java/.../SimulatorApplication.java`             | the simulator, an application of its own                                                                   |
| `simulator/src/main/java/.../loadrun/LoadRunController.java`        | the two URLs of a run; `load-run-api/load-run.yaml` describes them                                         |
| `simulator/src/main/java/.../config/LoadRunConfiguration.java`      | wires the driver and checks its settings at startup                                                        |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java` | the shared `isApproved()` and `payoutRunAt`, and the documents which give the aggregate its size           |
| `loan-approval/src/test/java/.../ScriptedCreditBureau.java`         | the credit bureau of the module test, which can stay silent                                                |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`               | each path of the process, one loan at a time                                                               |
| `simulator/src/test/java/.../loadrun/StoppingTheRunTest.java`       | a call the end of a run cuts off is no failure of the application, a refused call is                       |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                                            |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                                             |
| `docs/loan_approval.png`                                            | the picture of the process the README shows                                                                |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint. Copy them
unchanged.

## Adding this blueprint to an existing project

1. Write a load profile for your process, next to its BPMN files, with the keys of
   `load-profile.yaml`. Key every value by the id of the BPMN element it is about. Ask the
   person who knows the business for the values. They are not in the model.
2. Copy the Maven module `simulator` into the project, as an application of its own, with the
   parent its POM names. Copy the resource entry of its POM which copies the load profile in.
   Never add the simulator as a dependency of the application or of a workflow module: it must
   run in another process, or it measures itself.
3. Give the API your clerks and customers use a Maven module `<name>-api` with `client` and
   `server`, like `loan-approval-api`, if it has none. The simulator calls the `client`.
4. Adapt `Customers`, `Clerks` and `CreditBureauApiController` to that API and to the ids of
   your user tasks, gateways and catch events. Keep the rule that the driver only calls what a
   browser, a task list or a neighbour system would call.
5. Give each neighbour system of your module a port like `CreditBureau`, an OpenAPI file and a
   REST adapter, if it has none. The simulator implements the `server` side. In production the
   same adapter talks to the real system.
6. Put Micrometer with the Prometheus registry into the application
   (`quarkus-micrometer-registry-prometheus`), switch on the meters of the pool with
   `quarkus.datasource.metrics.enabled`, and set `vanillabp.metrics.gauge-cache` to `PT1S`.
   Without them, every queue reads as a dash.
7. Start the application with a JMX port for a run, as `application/pom.xml` does for
   `quarkus:dev`. Without it the simulator refuses to start a run, and the message names
   the options.
8. If the project uses another connection pool, change `connections-waiting-meter` and
   `pool-setting` in the `application.yaml` of the simulator.
9. Copy `LoadRunIT` and the plugins of `application/pom.xml` which start the simulator, with a
   tiny size. Put them into the plain `<build>`, not into a profile which switches itself on:
   such a profile switches off the BPMS profile which is active by default. It proves that the driver starts and finishes. Never assert a number of a run in
   a test.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

The database is a file, `target/loan-approval`, and it outlives the cluster. Against a new
cluster the start can end with the message "The BPMS behind adapter 'camunda8' is not the one
this application met before". That is VanillaBP's check for a new cluster, not a defect of the
generated code. `mvn clean` deletes the file.

Two tests have to pass:

- `LoanApprovalIT`: an answered credit score reaches the clerk, a silent bureau ends in an
  assessment without a score, an approved loan is paid out in the next payout run, a rejected
  one gets its rejection,
- `LoadRunIT`: the build starts the simulator, the test starts the application, and a tiny run
  walks through every stage, every workflow it started ends, and no request or decision fails.

A wrong key in the load profile stops the simulator at startup, and the message names the key.
Do not report success without having run this.
