# module-simulator

The systems around a workflow module are played by a simulator, a small application of its own.
The module knows them only through ports named after their roles, and the application chooses
the adapters. A delta on top of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                             Meaning                                              |
|----------------------------|--------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow module                                                              |
| `loanapproval`             | use case identifier, Java package                                                                |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module |
| `loan_approval`            | BPMN process ID                                                                                  |

Names this blueprint adds:

|                           Name                            |                                    What it is                                    |
|-----------------------------------------------------------|----------------------------------------------------------------------------------|
| `credit-rating`, `creditrating`                           | the synchronous system: Maven modules, Java packages, property prefix            |
| `e-signature`, `esignature`                               | the system which calls back: Maven modules, Java packages, property prefix       |
| `CreditRating`, `ContractSigning`                         | the ports, named after the role of the system. Name yours after the role as well |
| `credit-rating-api`, `e-signature-api`                    | the Maven modules of the APIs, with the OpenAPI files and the generated code     |
| `CreditRatingApi`, `EnvelopesApi`, `SignatureCallbackApi` | generated from the tags of the OpenAPI files, never written by hand              |
| `SignedContracts`                                         | the port the module implements, so an adapter can call back into it              |
| `blueprint.externalsystems`                               | base package of the ports and adapters                                           |
| `rest-adapter`, `credit-rating-rest-adapter`              | stand-in names; in a real project name the adapter after the system it talks to  |
| `blueprint.simulator`                                     | the package of the simulator                                                     |
| `blueprint.application`                                   | the application's package, deliberately NOT above the module                     |

**The rules this blueprint is built on:**

1. The workflow module depends on ports, never on adapters. A port is plain Java, named after
   the role of the system, with the types of the application. Only the application depends on
   adapters, outside the tests.
2. An adapter brings the producer of its bean and its own properties. Its address has no
   default. The application sets it per profile: `local` for the simulator, `real` for the real
   system.
3. The simulator plays every system, the Business Cockpit included, through the dev shell
   simulator it extends. No WireMock, no mock beans, no switch which turns the cockpit off.
4. The test API of the simulator lives below `/simulator/test-api/{system}/{action}` and knows
   nothing about the workflow. The caller gives the ids, or reserves one with `next-id`.
5. A test waits for the state of the workflow aggregate, never for a fixed time.
6. The service class says `@Transactional`, also where a workflow task already brings the
   transaction of VanillaBP.
7. Every API between the application and a system around it is an OpenAPI file in a Maven
   module `<api>-api`, with the modules `client` and `server`, which generate the Java code.
   The application uses `client`, the simulator uses `server`. A callback is a second OpenAPI
   file. `client` then holds the client of the API and the interface of the callback, `server`
   the opposite. No DTO, path or status code of the API is written by hand.
8. An adapter is named after the system it talks to, never after the technology:
   `sap-credit-rating-adapter`, `sign-now-e-signature-adapter`. The `rest-adapter` of this
   blueprint stands for such a name only because its systems are made up. The port keeps the
   name of the role.

## Core files

|                                             File                                             |                                                      Why it matters                                                       |
|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `external-systems/credit-rating/port/src/main/java/.../CreditRating.java`                    | a port: an interface named after the role, with the types of the application                                              |
| `external-systems/e-signature/port/src/main/java/.../SignedContracts.java`                   | the port the module implements, so the callback reaches the module without a dependency on the adapter                    |
| `external-systems/credit-rating/credit-rating-api/credit-rating.yaml`                        | the API of the credit rating, the one source of its client and of its server interface                                    |
| `external-systems/e-signature/e-signature-api/e-signature.yaml`                              | the API of the e-signature service                                                                                        |
| `external-systems/e-signature/e-signature-api/e-signature-callback.yaml`                     | the API of the callback, which the application offers and the service calls                                               |
| `external-systems/e-signature/e-signature-api/client/pom.xml`                                | generates the client of the API and the interface of the callback: what the application needs                             |
| `external-systems/e-signature/e-signature-api/server/pom.xml`                                | generates the interface of the API and the client of the callback: what the simulator needs                               |
| `external-systems/credit-rating/rest-adapter/src/main/java/.../CreditRatingRestAdapter.java` | calls the generated client and translates its answer into the port, and a failure into `CreditRatingUnavailableException` |
| `external-systems/e-signature/rest-adapter/src/main/java/.../CallbackController.java`        | implements the generated callback interface and calls `SignedContracts`                                                   |
| `external-systems/*/rest-adapter/src/main/java/.../config/*Producer.java`                    | the adapter wires itself and stops the start when its address is missing                                                  |
| `loan-approval/src/main/java/.../Service.java`                                               | uses the ports by their role: `creditRating`, `contractSigning`                                                           |
| `loan-approval/src/main/java/.../SignedContractsHandler.java`                                | implements `SignedContracts` and calls `Service`                                                                          |
| `loan-approval/src/main/java/.../ApiController.java`                                         | the 503 with the role of the system, as a problem detail                                                                  |
| `loan-approval/src/main/resources/.../loan_approval.bpmn`                                    | user task, service task, message catch event "Contract signed"                                                            |
| `simulator/src/main/java/blueprint/simulator/SimulatorApplication.java`                      | extends `DevShellSimulator` and scans both packages; an application of its own, not of this platform                      |
| `simulator/src/main/java/blueprint/simulator/*/*ApiController.java`                          | the API of a system: implements the generated server interface, and adds what the system does                             |
| `simulator/src/main/java/blueprint/simulator/*/*TestApiController.java`                      | the test API, one controller per simulated system                                                                         |
| `loan-approval/pom.xml`                                                                      | the profile `start-stop-simulator`: free port, runnable JAR, start, log file                                              |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                        | the whole case against the simulator, with all three systems                                                              |
| `loan-approval/src/test/java/.../SimulatorExtension.java`                                    | skips without a simulator, fails when the build started one which does not answer, prints its log on a red test           |
| `loan-approval/src/test/java/.../SimulatorTestApi.java`                                      | the client of the test API and of the cockpit API                                                                         |
| `loan-approval/src/test/java/.../EverySystemIsTheSimulator.java`                             | points every adapter at the simulator and picks the port of the application, before it starts                             |
| `application/src/main/resources/application-local.yaml`                                      | every adapter points at the simulator                                                                                     |
| `application/src/main/resources/application-real.yaml`                                       | every adapter points at the real system, addresses from the environment                                                   |
| `application/src/test/java/.../ExternalSystemUnavailableIT.java`                             | proves the 503 in the profile `real`                                                                                      |

## Boilerplate files

|                                File                                 |                                                   Purpose                                                   |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the Maven modules in build order, the versions, the enforcer rule and the settings of the OpenAPI generator |
| `external-systems/*/*-api/client/pom.xml`                           | the generated Jakarta REST interfaces, and the index Quarkus finds them in                                  |
| `external-systems/*/*-api/server/pom.xml`                           | the generated interfaces of the simulator, with the parent and the generator settings of its platform       |
| `external-systems/*/*-api/pom.xml`                                  | groups `client` and `server`, no code of its own                                                            |
| `external-systems/credit-rating/credit-rating-api/*/pom.xml`        | one generator execution each, `client` or `server`                                                          |
| `external-systems/*/port/pom.xml`                                   | no dependencies at all                                                                                      |
| `external-systems/*/rest-adapter/pom.xml`                           | the port, the generated `client`, the REST client of Quarkus, and the index                                 |
| `simulator/pom.xml`                                                 | its own parent, `dev-shell-simulator`, the `server` modules, and the JAR with the classifier `runnable`     |
| `simulator/src/main/resources/application.yaml`                     | port 8079, the users of the simulated cockpit, the start data                                               |
| `application/pom.xml`                                               | the module, the adapters, and the BPMS and cockpit adapters per BPMS profile                                |
| `application/src/main/resources/application.yaml`                   | the database, and the two parent profiles: the BPMS and `local` or `real`                                   |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the module's own configuration, including what the cockpit has to know                                      |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test, identical in every blueprint                                            |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application with the parent profile `local`, identical in every blueprint                         |
| `docs/loan_approval.png`                                            | the picture of the process the README shows                                                                 |

## Adding this blueprint to an existing project

1. For every system the workflow module talks to, create a Maven module `port` with an
   interface named after the role of the system and the types the module wants to see. Add a
   runtime exception which names the role, for the case that the system does not answer.
2. Make the workflow module depend on the ports and remove every client of a system from it.
   Name the injected port after its role, for example `creditRating`.
3. For an answer which comes later, such as a callback, add an interface to the port which the
   module implements, like `SignedContracts`. The adapter calls it.
4. Create a Maven module `<api>-api` per system, named after its API. Put its OpenAPI file
   there, from the vendor if there is one. Give each operation a tag, because the generated
   interface is named after it. Add the modules `client` and `server`, which run the
   `openapi-generator-maven-plugin` with the settings of the root POM of this blueprint. For a
   system which calls back, write a second OpenAPI file for the callback. `client` generates
   the client of the API and the interface of the callback, `server` the opposite.
5. Create a Maven module `rest-adapter` per system. It depends on the port, on `client` and on
   `quarkus-rest-client-jackson`. It implements the port with the generated client, built by
   `QuarkusRestClientBuilder`. A callback endpoint is a bean which implements the generated
   callback interface. Add a `@ConfigMapping` for the address, without a default, and a producer
   of the port's bean with `@Startup`. Fail the start with a message which names the missing
   key. Index the module and `client` with the `jandex-maven-plugin`.
6. Make the application depend on the adapters. Set their addresses in
   `application-local.yaml` (the simulator) and `application-real.yaml` (from the
   environment). Make one of them a parent profile with `quarkus.config.profile.parent`, next
   to the BPMS.
7. Create the simulator in the same way as this one, or copy it: an application which extends
   `DevShellSimulator`, scans its own package and the one of `DevShellSimulator`, plays each
   system's API with controllers which implement the interfaces of the `server` modules, and
   offers a test API below `/simulator/test-api/{system}/{action}`. Give it users for the
   cockpit and a JWT key in its `application.yaml`, because its own file replaces the one of
   the dev shell simulator. Package it with the classifier `runnable`. The simulator and the
   `server` modules use the platform of the dev shell simulator. Keep its parent in their POMs,
   and never make the application depend on them.
8. Copy the profile `start-stop-simulator` into the POM of the module which has the
   integration tests, and the test scope dependency on the simulator with `<type>pom</type>`,
   which only orders the build. The order of `<modules>` is not enough, above all in a
   parallel build (`-T`).
9. Copy `SimulatorExtension`, `SimulatorTestApi` and `EverySystemIsTheSimulator`, and write the
   integration test against the simulator, with `@WithTestResource(EverySystemIsTheSimulator.class)`.
   Wait for the aggregate after every step.
10. Map the exception of a system the user waits for to a 503 which names its role.

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

`LoanApprovalIT` has to pass with all of its tests, not be skipped. Under the build it fails
instead of skipping when the simulator does not answer. A red test prints what the simulator
logged meanwhile, and `loan-approval/target/simulator.log` has the whole log.
`ExternalSystemUnavailableIT` and `ApplicationSmokeTest` have to pass as well. If the
application does not start because an address is missing, step 5 is incomplete. If a callback
answers 404, the contract id is not the id the module looks the aggregate up by. If a module does
not compile after a change of an OpenAPI file, the generated code changed, and the adapter or
the simulator has to follow it.

Do not report success without having run this.
