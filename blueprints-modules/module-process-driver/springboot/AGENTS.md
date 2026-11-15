# module-process-driver

Scenarios bring a real loan approval to every point of its process. A process driver runs the
steps of a named state through the business code of the workflow module and the test API of the
simulator, and the test starts at that state. The driver is a Maven module of its own. A delta
on top of `module-simulator`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do. Read
the `AGENTS.md` of `module-simulator` as well. This blueprint keeps its simulator, its ports and
its adapters as they are.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                             Meaning                                              |
|----------------------------|--------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow module                                                              |
| `loanapproval`             | use case identifier, Java package                                                                |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module |
| `loan_approval`            | BPMN process ID                                                                                  |

Names this blueprint adds:

|                    Name                    |                                      What it is                                      |
|--------------------------------------------|--------------------------------------------------------------------------------------|
| `test-driver`                              | the Maven module of the driver, and of the tests of the workflow module              |
| `blueprint.testdriver.loanapproval`        | the package of the driver, deliberately NOT below the package of the workflow module |
| `blueprint.testdriver.simulator`           | `SimulatorTestApi`, the client of the test API of the simulator                      |
| `ProcessState`                             | the states a scenario can reach, each with the list of its steps                     |
| `*Step`                                    | one step on the way, named after what is true when it is done                        |
| `TestData`                                 | what a scenario says about the case, with defaults which make sense for the business |
| `process-driver.*`                         | the configuration of the driver: `enabled`, `simulator-base-url`, `timeout`          |
| `/process-driver/states`, `.../advance-to` | the REST API of the driver, for the dev shell and the tests of a user interface      |

**The rules this blueprint is built on:**

1. A test never plays the history of its case. It calls `advanceTo(state, testData)` and starts
   there. The steps on the way are in `ProcessState` and nowhere else.
2. A state names a point where the workflow waits, together with its history. Two states may
   name the same task. `_USER_TASK` at the end means the task is open, and the driver leaves it
   open.
3. The driver never talks to a BPMS, not even through `ProcessService`. It uses the business code
   of the module (`Service`), the repository of the aggregates and the test API of the
   simulator. `Workflow` stays the only class which talks to the process.
4. A branch is chosen by the test data. A step on the way completes the task of a branch if the
   test data chose it, and does nothing otherwise. A step refuses test data which cannot lead to
   its state before it waits.
5. Wait for the wait point before you signal: complete a task only once its id is in the
   aggregate. Wait until your own state is stable before you return: the last step waits for
   what the workflow writes after the engine moved on.
6. A timeout names the step and says why, from the aggregate as it was last seen.
7. Every read and every call of the business code in the driver runs in a transaction of its
   own and clears the entity manager first. In a request of the REST API, the entity manager of
   the request would otherwise answer from its cache.
8. The driver is in its own Maven module, which production never pulls. The application takes
   it only with the Maven profile `test-driver`, and the driver does nothing until
   `process-driver.enabled` is `true`.
9. The tests of the workflow module are in `test-driver`, because the driver needs the module.
   A module which depended on its own driver in the test scope would be a cycle, which Maven
   rejects.

## Core files

|                                       File                                       |                                         Why it matters                                          |
|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `test-driver/src/main/java/.../ProcessState.java`                                | the states and the steps of each. The only place where the tests know the process               |
| `test-driver/src/main/java/.../ProcessDriver.java`                               | `advanceTo`: runs the steps of a state and logs where the case is                               |
| `test-driver/src/main/java/.../TestData.java`                                    | the defaults of a scenario, and the withers a test changes them with                            |
| `test-driver/src/main/java/.../steps/StepSupport.java`                           | waits for the aggregate in transactions of its own, turns a timeout into the reason             |
| `test-driver/src/main/java/.../steps/RiskAssessedOnTheWayStep.java`              | the branch: completes "Assess risk" only if the amount needs it                                 |
| `test-driver/src/main/java/.../steps/RiskAssessmentOpenedStep.java`              | refuses a small amount at once, before it waits                                                 |
| `test-driver/src/main/java/.../steps/ContractCheckOpenedStep.java`               | the last wait of a state: for what the workflow writes after the engine moved on                |
| `test-driver/src/main/java/.../ProcessDriverController.java`                     | the REST API of the driver, implements the interface generated from `process-driver.yaml`       |
| `test-driver/src/main/java/.../config/ProcessDriverAutoConfiguration.java`       | wires the driver only if `process-driver.enabled` is `true`                                     |
| `test-driver/src/main/resources/openapi/process-driver.yaml`                     | the REST API of the driver as an OpenAPI file                                                   |
| `test-driver/src/main/java/blueprint/testdriver/simulator/SimulatorTestApi.java` | the client of the test API and of the cockpit API the simulator plays                           |
| `test-driver/pom.xml`                                                            | the driver, the tests of the module, the start of the simulator, the BPMS profiles of the tests |
| `test-driver/src/test/java/.../ScenarioTest.java`                                | base class of the tests: one application for all of them, pointed at the simulator              |
| `test-driver/src/test/java/.../CheckContractIT.java`                             | one task, two scenarios: a small and a large amount                                             |
| `test-driver/src/test/java/.../CheckContractWithoutSignatureIT.java`             | the same task in the other situation: 409 until the customer signed                             |
| `test-driver/src/test/java/.../ProcessDriverIT.java`                             | the REST API of the driver, and the reason for a state the test data cannot reach               |
| `loan-approval/src/main/java/.../Service.java`                                   | writes the id of each open task into the aggregate, refuses to check an unsigned contract       |
| `loan-approval/src/main/java/.../model/Aggregate.java`                           | `riskAssessmentNeeded` as a plain `boolean` for the gateway, the ids of the open tasks          |
| `loan-approval/src/main/resources/.../loan_approval.bpmn`                        | the gateway, "Assess risk", "Send contract for signing", "Check contract"                       |
| `application/pom.xml`                                                            | the Maven profile `test-driver`, which a production build never names                           |

## Boilerplate files

|                                 File                                 |                                                   Purpose                                                   |
|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                           | the Maven modules in build order, the versions, the enforcer rule and the settings of the OpenAPI generator |
| `loan-approval/pom.xml`                                              | the workflow module without tests: they are in `test-driver`                                                |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml`  | the module's own configuration, with the limit of the risk assessment                                       |
| `loan-approval/src/main/java/.../config/LoanApprovalProperties.java` | `minimumCreditScore` and `riskAssessmentLimit`                                                              |
| `test-driver/src/main/java/.../config/ProcessDriverProperties.java`  | `enabled`, `simulatorBaseUrl`, `timeout`                                                                    |
| `test-driver/src/test/resources/application.yaml`                    | the database of the tests, and `process-driver.enabled: true`                                               |
| `test-driver/src/test/java/.../ModuleTestApplication.java`           | boots the module and the driver through their auto-configurations                                           |
| `test-driver/src/test/java/.../WorkflowModuleTest.java`              | base class of the integration test, identical in every blueprint                                            |
| `test-driver/src/test/java/.../SimulatorExtension.java`              | skips without a simulator, and prints its log when a test fails                                             |
| `test-driver/src/test/java/.../ProcessDriverSwitchTest.java`         | the driver is off unless the property says otherwise                                                        |
| `application/src/test/java/.../ApplicationSmokeTest.java`            | boots the application in the profile `local`, identical in every blueprint                                  |
| `docs/loan_approval.png`                                             | the picture of the process the README shows                                                                 |

## Adding this blueprint to an existing project

1. Start from a project which has a simulator, as `module-simulator` shows. The driver needs
   the test API to prepare data and to make a system act.
2. Make sure the business code writes a trace into the aggregate at every wait state: the id
   of each open user task, and a flag for each answer of a system. The driver waits for these
   and nothing else.
3. Create a Maven module `test-driver`. It depends on the workflow module, on the client of the
   test API, on `spring-boot-starter-web`, on `spring-boot-starter-restclient` and on
   `awaitility`. Put it in a package of its own, outside the package the module scans.
4. Move the client of the test API into `test-driver`, and the tests of the workflow module,
   their resources and the plugins which start the simulator as well. Take the BPMS profiles of
   the tests along. The workflow module keeps no tests.
5. Write one `Step` per thing that happens on the way. Each step waits for the aggregate at its
   end, through `StepSupport`, and passes a diagnosis which says why the state was not reached.
   Run every call of the business code through `StepSupport.inOwnTransaction`.
6. For every branch the test data can choose, write a step which completes the tasks of the
   branch if the test data chose it, and does nothing otherwise.
7. Write `ProcessState` with one value per point a test needs, and the list of its steps. Keep
   it short. A state nobody tests is not worth keeping.
8. Write `TestData` with defaults which make sense for the business, and withers for what the
   scenarios change.
9. Add the auto-configuration behind `process-driver.enabled`, register it in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, and add
   the REST API as an OpenAPI file with a controller which implements the generated interface.
10. Add a Maven profile `test-driver` to the application, which adds the module. Never add it
    to the plain dependencies of the application.
11. Rewrite the tests: one class per task, `advanceTo` in the test or in `@BeforeEach`, then act
    through the API of the module and the test API, and wait for the aggregate.

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

All tests of `test-driver` have to pass, not be skipped: `AssessRiskIT`, `CheckContractIT`,
`CheckContractWithoutSignatureIT`, `LoanRequestIT`, `ProcessDriverIT` and
`ProcessDriverSwitchTest`. `ApplicationSmokeTest` and `ExternalSystemUnavailableIT` in the
application have to pass as well. A `ProcessStateNotReachedException` names the step and the
reason. Read it before you change a test: a test which waits longer hides the fault. If the
driver sees no progress only when it runs through its REST API, a read or a call bypasses
`StepSupport` and gets an answer from the cache of the request.

Do not report success without having run this.
