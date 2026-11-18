# module-process-driver

Scenarios bring a real loan approval to every point of its process. The simulator plays the
environment, every system around the application. The process driver plays the users: it runs
the steps of a named state through the API of the user interface of the workflow module and the
test API of the simulator, and the test starts at that state. The driver is a Maven module of its
own, and the tests of the workflow module live in the workflow module. A delta on top of
`module-simulator`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do. Read
the `AGENTS.md` of `module-simulator` as well. This blueprint keeps its ports and adapters. It
moves the simulator into `simulator/application` and its test API into `simulator/api`, and it
makes the simulated e-signature service act on the name of the signer.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                             Meaning                                              |
|----------------------------|--------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow module                                                              |
| `loanapproval`             | use case identifier, Java package                                                                |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module |
| `loan_approval`            | BPMN process ID                                                                                  |

Names this blueprint adds:

|                             Name                             |                                               What it is                                               |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `loan-approval/workflow-module`                              | the workflow module, artifact `loan-approval-workflow-module`, with all its tests                      |
| `loan-approval/test-driver`                                  | the process driver, artifact `loan-approval-test-driver`                                               |
| `simulator/api`, `simulator/application`                     | the test API (`simulator-api-client`, `simulator-api-server`) and the simulator itself                 |
| `blueprint.testdriver.loanapproval`                          | the package of the driver, deliberately NOT below the package of the workflow module                   |
| `blueprint.testdriver.loanapproval.gui`                      | the client of the API of the user interface, generated from `loan-approval/openapi/loan-approval.yaml` |
| `blueprint.testdriver.simulator`                             | `SimulatorTestApi`, the client of the test API of the simulator                                        |
| `ProcessState`                                               | the states a scenario can reach, each with the step which finishes it                                  |
| `*Step`                                                      | one step, named after what is true when it is done, with its direct predecessors                       |
| `TestData`                                                   | what a scenario says about the case, with defaults which make sense for the business                   |
| `John Signing`, `Jack Nosign`, ...                           | signers the simulated e-signature service acts on, see the table in the README                         |
| `process-driver.*`                                           | the configuration of the driver: `enabled`, `simulator-base-url`, `application-base-url`, `timeout`    |
| `/loan-approval/api/process-driver/states`, `.../advance-to` | the REST API of the driver, for the dev shell and the tests of a user interface                        |

**The rules this blueprint is built on:**

1. The simulator plays the environment, and the driver plays the users. The application talks
   only to the simulator and is called back only by it. The driver uses only the API of the
   user interface and the test API of the simulator.
2. A simulated system acts like the real one. A test chooses a behaviour by the data it hands
   over, like the name of a signer, or by a call of the test API for what data cannot say. Use
   both to test how the application copes with a system which misbehaves.
3. A test never plays the history of its case. It calls `advanceTo(state, testData)` and starts
   there. The way to a state is in `ProcessState` and in the steps, and nowhere else. A step
   names only the steps right before it, in `prerequisites()`, and a state names only the step
   which finishes it. The driver follows the names back to the start, runs each step once and
   checks all states when it is created. Never list the whole way in one place: a new task
   would then change every list.
4. A state names a point where the workflow waits, together with its history. Two states may
   name the same task. `_USER_TASK` at the end means the task is open, and the driver leaves it
   open. A state may also be an end of the workflow, like `CONTRACT_DECLINED`. Then no task is
   open.
5. The driver never talks to a BPMS, and it knows no Java class of the workflow module. It acts
   and reads through the generated client of the API of the user interface. `Workflow` stays
   the only class which talks to the process.
6. A branch is chosen by the test data. A step on the way completes the task of a branch if the
   test data chose it, and does nothing otherwise. A step which needs something of the test data
   says so in `prepare()`, for example a customer who signs. A step refuses test data which
   cannot lead to its state before it waits.
7. Wait for the wait point before you signal: complete a task only once the API shows its id.
   Wait until your own state is stable before you return: the last step waits for what the
   workflow writes after the engine moved on. A message which a simulated system causes may
   come before the workflow waits for it, so the module gives its messages a time-to-live.
   Never wait for a task which such a message may already have taken away.
8. A timeout names the step and says why, from the loan request as the API showed it last.
9. The driver is in its own Maven module, which production never pulls. The application takes
   it only with the Maven profile `test-driver`, and the driver does nothing until
   `process-driver.enabled` is `true`.
10. The tests of the workflow module are in the workflow module, which depends on the driver in
    the test scope. The driver does not depend on the module, so there is no cycle. JaCoCo
    measures the module from these tests. The coverage is there to show that tests which use
    the software like in production reach most of its code; write a test for an acceptance
    criterion of the story, not for a line the report shows as missed.
11. Tests and helpers say what happens in the business: `aClerkAssessesTheRiskInTheBrowser`,
    `theFormOfTheTaskSubmitsTheAssessment`. Every HTTP call goes through a generated client.

## Core files

|                                              File                                              |                                                       Why it matters                                                        |
|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/test-driver/src/main/java/.../ProcessState.java`                                | the states and the step which finishes each; with the steps, all the tests know of the process                              |
| `loan-approval/test-driver/src/main/java/.../ProcessDriver.java`                               | follows the prerequisites of the steps; `advanceTo` prepares the test data, runs the steps and logs where the case is       |
| `loan-approval/test-driver/src/main/java/.../TestData.java`                                    | the defaults of a scenario, the signers the simulator acts on, and the withers a test changes them with                     |
| `loan-approval/test-driver/src/main/java/.../steps/StepSupport.java`                           | waits until the API shows the loan request in a state, turns a timeout into the reason                                      |
| `loan-approval/test-driver/src/main/java/.../steps/RiskAssessedOnTheWayStep.java`              | the branch: submits the form of "Assess risk" only if the amount needs it                                                   |
| `loan-approval/test-driver/src/main/java/.../steps/RiskAssessmentOpenedStep.java`              | refuses a small amount at once, before it waits                                                                             |
| `loan-approval/test-driver/src/main/java/.../steps/ContractSignedStep.java`                    | chooses the customer who signs by himself in `prepare()`, and waits for the signature                                       |
| `loan-approval/test-driver/src/main/java/.../steps/ContractDeclinedStep.java`                  | chooses the customer who declines by himself, and waits until the workflow took "Check contract" away                       |
| `loan-approval/test-driver/src/main/java/.../steps/ContractCheckOpenedStep.java`               | the last wait of a state: for what the workflow writes after the engine moved on                                            |
| `loan-approval/test-driver/src/main/java/.../ProcessDriverController.java`                     | the REST API of the driver, implements the interface generated from `process-driver.yaml`                                   |
| `loan-approval/test-driver/src/main/java/.../config/ProcessDriverProducer.java`                | makes the two clients; it and every other bean of the driver say `@IfBuildProperty` for `process-driver.enabled`            |
| `loan-approval/test-driver/src/main/resources/loan-approval/openapi/process-driver.yaml`       | the REST API of the driver as an OpenAPI file                                                                               |
| `loan-approval/test-driver/src/main/java/blueprint/testdriver/simulator/SimulatorTestApi.java` | the client of the test API of the simulator                                                                                 |
| `loan-approval/test-driver/pom.xml`                                                            | the driver; generates the client of the API of the user interface from the file of the module                               |
| `loan-approval/workflow-module/pom.xml`                                                        | the module, its tests with the driver in the test scope, the start of the simulator, JaCoCo, the BPMS profiles of the tests |
| `loan-approval/workflow-module/src/test/java/.../ScenarioTest.java`                            | base class of the tests: one application for all of them, pointed at the simulator                                          |
| `loan-approval/workflow-module/src/test/java/.../EverySystemIsTheSimulator.java`               | points every adapter and the driver at the simulator and picks the port of the application, before it starts                |
| `loan-approval/workflow-module/src/test/java/.../CheckContractIT.java`                         | one task, two scenarios: a small and a large amount                                                                         |
| `loan-approval/workflow-module/src/test/java/.../CheckContractWithoutSignatureIT.java`         | the same task in the other situation: refused until the customer signed, in the browser and in the form                     |
| `loan-approval/workflow-module/src/test/java/.../ContractSigningIT.java`                       | one test per behaviour of the simulated e-signature service, the misbehaving ones included                                  |
| `loan-approval/workflow-module/src/test/java/.../ProcessDriverIT.java`                         | the REST API of the driver, and the reason for a state the test data cannot reach                                           |
| `loan-approval/workflow-module/src/main/java/.../ApiController.java`                           | completes each task with GET, for a person, and with POST, for its form; the 409; the loan request as JSON                  |
| `loan-approval/workflow-module/src/main/resources/loan-approval/openapi/loan-approval.yaml`    | the API of the use case; the form calls `completeAssessRisk` and `completeCheckContract`, the driver reads `show`           |
| `loan-approval/workflow-module/src/main/java/.../Service.java`                                 | writes the id of each open task into the aggregate, refuses to check an unsigned contract, correlates the decline           |
| `loan-approval/workflow-module/src/main/java/.../model/Aggregate.java`                         | `riskAssessmentNeeded` as a plain `boolean` for the gateway, the ids of the open tasks, `@DynamicUpdate`                    |
| `loan-approval/workflow-module/src/main/resources/.../loan_approval.bpmn`                      | the gateway, "Assess risk", "Send contract for signing", "Check contract" with its message "Contract declined"              |
| `loan-approval/workflow-module/src/main/java/.../WorkflowTaskHandler.java`                     | `@TaskEvent` on "Check contract": the aggregate drops the id when the decline takes the task away                           |
| `simulator/application/src/main/java/.../esignature/SignerBehaviour.java`                      | what the simulated e-signature service does for each signer                                                                 |
| `simulator/api/simulator-test-api.yaml`                                                        | the test API: sign now, a report of any contract, the reports sent, the refusals                                            |
| `application/pom.xml`                                                                          | the Maven profile `test-driver`, which a production build never names                                                       |

## Boilerplate files

|                                         File                                         |                                              Purpose                                               |
|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                           | the Maven modules, the versions, the enforcer rule and the settings of the OpenAPI generator       |
| `loan-approval/pom.xml`                                                              | the aggregator of the workflow module and the driver                                               |
| `simulator/pom.xml`                                                                  | the aggregator of the test API and the simulator                                                   |
| `loan-approval/workflow-module/src/main/resources/loan-approval/loan-approval.yaml`  | the module's own configuration, with the limit of the risk assessment and the message time-to-live |
| `loan-approval/workflow-module/src/main/java/.../config/LoanApprovalProperties.java` | `minimumCreditScore` and `riskAssessmentLimit`                                                     |
| `loan-approval/test-driver/src/main/java/.../config/ProcessDriverProperties.java`    | `enabled`, `simulatorBaseUrl`, `applicationBaseUrl`, `timeout`                                     |
| `loan-approval/test-driver/src/main/java/blueprint/testdriver/GeneratedClients.java` | turns a generated HTTP interface into a client                                                     |
| `loan-approval/workflow-module/src/test/resources/application.yaml`                  | the database of the tests, and `process-driver.enabled: true`, a build property                    |
| `loan-approval/workflow-module/src/test/java/.../WorkflowModuleTest.java`            | base class of the integration test, identical in every blueprint                                   |
| `loan-approval/workflow-module/src/test/java/.../SimulatorExtension.java`            | skips without a simulator, and prints its log when a test fails                                    |
| `loan-approval/workflow-module/src/test/java/.../SuppressOutputExtension.java`       | keeps a green build log empty and shows the output of a failing test, from the test harness        |
| `loan-approval/test-driver/src/test/java/.../ProcessDriverSwitchTest.java`           | the driver is off unless the property says otherwise; Quarkus builds a small application for it    |
| `loan-approval/test-driver/src/test/java/.../StepOrderTest.java`                     | the order of the steps, without an application                                                     |
| `application/src/test/java/.../ApplicationSmokeTest.java`                            | boots the application with the parent profile `local`, identical in every blueprint                |
| `docs/loan_approval.png`                                                             | the picture of the process the README shows                                                        |

## Adding this blueprint to an existing project

1. Start from a project which has a simulator, as `module-simulator` shows. The driver needs
   the test API to prepare data and to make a system act.
2. Teach the simulator the typical behaviours of each real system, and choose them by the data
   it gets where you can, like the signers of `SignerBehaviour`. Add a call to the test API only
   for what data cannot say. Let the test API show what a system got and what it did.
3. Make sure the business code writes a trace into the aggregate at every wait state: the id
   of each open user task, and a flag for each answer of a system. Show it in the API of the
   user interface, as `show` does. The driver waits for these and nothing else. Put
   `@DynamicUpdate` on the aggregate if a system may call back while the workflow writes it.
4. Turn the Maven module of the workflow module into `<use case>/workflow-module` below an
   aggregator `<use case>`, and the simulator and its test API into `simulator/application` and
   `simulator/api`.
5. Create the Maven module `<use case>/test-driver`. It depends on the client of the test API,
   on `quarkus-rest-jackson`, on `quarkus-rest-client-jackson` and on `awaitility`, but never on
   the workflow module. It generates the client of the API of the user interface from the
   OpenAPI file of the module, by its path. Put it in a package of its own, outside the package
   of the module, and index it with the `jandex-maven-plugin`, so Quarkus finds its beans, its
   REST API and the generated interfaces.
6. Give the workflow module the driver as a test dependency, and keep its tests, their
   resources, the plugins which start the simulator, JaCoCo and the BPMS profiles of the tests
   there. On Quarkus the test dependency `quarkus-jacoco` measures, not the agent of JaCoCo,
   and the JaCoCo plugin only checks its file. Leave the classes generated from the OpenAPI file
   out of both.
7. Write one `Step` per thing that happens on the way. Each step acts through the generated
   client, waits through `StepSupport` until the API shows the result, and passes a diagnosis
   which says why the state was not reached. Let each step name the steps right before it in
   `prerequisites()`, and only those. A step which needs certain test data says so in
   `prepare()`.
8. For every branch the test data can choose, write a step which completes the tasks of the
   branch if the test data chose it, and does nothing otherwise.
9. Write `ProcessState` with one value per point a test needs, and the step which finishes it. Keep
   it short. A state nobody tests is not worth keeping.
10. Write `TestData` with defaults which make sense for the business, and withers for what the
    scenarios change.
11. Put `@IfBuildProperty(name = "process-driver.enabled", stringValue = "true")` on every bean
    of the driver: the steps, `StepSupport`, `ProcessDriver`, the producer of the two clients and
    the controller. Make the steps `@Singleton`, so the driver finds each one by its class and not
    by the class of a proxy. Give `ProcessDriver` `@Startup`, so a wrong step stops the start. Add
    the REST API as an OpenAPI file with a controller which implements the generated interface.
    Set `process-driver.enabled: true` in the `application.yaml` of the tests of the workflow
    module. It is a build property, so the tests read it while Quarkus builds the application
    under test.
12. Add a Maven profile `test-driver` to the application, which adds the driver. Never add it
    to the plain dependencies of the application.
13. Copy `EverySystemIsTheSimulator`, which points the adapters and the driver at the simulator
    and the driver at the application, and give the base class of the tests
    `@WithTestResource(EverySystemIsTheSimulator.class)`. Rewrite the tests: one class per task, `advanceTo` in the test or in `@BeforeEach`, then act
    through the generated clients of the API of the module and of the test API, and wait for the
    aggregate. Name the helpers after what happens in the business. Write one test per
    behaviour of a simulated system, the misbehaving ones included.

## Verifying

```bash
mvn verify
```

That runs on Camunda 7, which is embedded, and the build starts the simulator.

```bash
mvn verify -Pcamunda8
```

`-Pcamunda8` needs a running cluster and a configured
`vanillabp.adapters.camunda8.rest-address`. Check both before you report a failure of that
profile as a defect of the generated code.

All tests of the workflow module have to pass, not be skipped: `AssessRiskIT`,
`CheckContractIT`, `CheckContractWithoutSignatureIT`, `ContractSigningIT`, `LoanRequestIT` and
`ProcessDriverIT`, and the coverage check after them. The unit tests of the driver,
`ProcessDriverSwitchTest` and `StepOrderTest`, run in `test-driver`. `ApplicationSmokeTest` and
`ExternalSystemUnavailableIT` in the application have to pass as well. A
`ProcessStateNotReachedException` names the step and the reason. Read it before you change a
test: a test which waits longer hides the fault.

Do not report success without having run this.
