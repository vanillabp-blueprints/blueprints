# module-interaction

Two workflow modules which need each other: one asks, the other answers, and neither names
the other. What crosses the border is an interface for the request and an event for the
answer, both in a JAR of their own. A delta on top of `module-multi`, whose conventions
(a module wiring itself, bean names per module, name clash avoidance) are used unchanged.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It carries the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                                          Meaning                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package of the workflow modules                                                                                      |
| `loanapproval`             | use case identifier, Java package                                                                                         |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name |
| `loan_approval`            | BPMN process ID                                                                                                           |

Names this blueprint adds, because it has more than one of everything:

|          Name           |                                          What it is                                          |
|-------------------------|----------------------------------------------------------------------------------------------|
| `riskassessment`        | the answering use case, Java package (`risk-assessment` kebab, `risk_assessment` process ID) |
| `banking-api`           | the JAR both modules depend on, Java package `blueprint.api`                                 |
| `blueprint.application` | the application's package, deliberately NOT above the modules                                |

**The rules this blueprint is built on:**

1. Two BPMN models never talk to each other. A process asks its own business code, that code
   calls an interface, and the other module's business code answers - the models stay
   unaware of one another.
2. The request is a method call against an interface in a JAR both modules depend on. The
   answer is an event, because a module which called back would know its callers.
3. Nothing else crosses: no aggregate, no BPMN, no `ProcessService`, no task handler, no
   entity. The id of the business case does, as a string.
4. Everything about having two modules at all comes from `module-multi` and is not repeated
   here.

## Core files

|                                   File                                   |                                                  Why it matters                                                   |
|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `banking-api/src/main/java/blueprint/api/RiskAssessments.java`           | the interface one module offers the other. The asking module compiles against it, the answering one implements it |
| `banking-api/src/main/java/blueprint/api/RiskAssessed.java`              | the event carrying the answer back, because the answering module must not know its callers                        |
| `loan-approval/src/main/java/.../loanapproval/Service.java`              | asks through the interface and takes the answer onto its own aggregate                                            |
| `loan-approval/src/main/java/.../loanapproval/RiskAssessedListener.java` | where the event enters this module and becomes a correlated message. A driving adapter, like the API              |
| `loan-approval/src/main/resources/.../loan_approval.bpmn`                | asks in a service task, waits in a message event, names neither the other module nor its process                  |
| `risk-assessment/src/main/java/.../riskassessment/Service.java`          | implements the interface, runs its own process, publishes the result                                              |
| `risk-assessment/src/main/resources/.../risk_assessment.bpmn`            | does the work, then publishes: the answer leaves the module at a task the model shows                             |
| `application/src/test/java/.../ModuleInteractionIT.java`                 | both modules in one application: the answer reaches the waiting process                                           |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                    | the asking module alone, with the other one stood in for by a stub of a few lines                                 |
| `risk-assessment/src/test/java/.../RiskAssessmentIT.java`                | the answering module alone, called exactly as the other module calls it                                           |

## Boilerplate files

|                                         File                                         |                                              Purpose                                              |
|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                           | the four Maven modules, the BPMS profiles and the VanillaBP BOM import                            |
| `banking-api/pom.xml`                                                                | no VanillaBP dependency at all: what crosses the module border knows nothing about processes      |
| `loan-approval/pom.xml`, `risk-assessment/pom.xml`                                   | `vanillabp-spring-boot-support` and the API JAR, never an adapter outside the test scope          |
| `application/pom.xml`                                                                | the two module dependencies, the BPMS adapter, and the profiles every test of this module runs in |
| `.../LoanApprovalAutoConfiguration.java`, `.../RiskAssessmentAutoConfiguration.java` | each module wires itself, as in `module-multi`                                                    |
| `.../LoanApprovalBeanNames.java`, `.../RiskAssessmentBeanNames.java`                 | bean names per module, as in `module-multi`                                                       |
| `loan-approval/src/test/java/.../ModuleTestApplication.java`                         | the minimal application a module test boots: no scan, the auto-configuration does the wiring      |
| `docs/loan_approval.png`, `docs/risk_assessment.png`                                 | the pictures of the two processes the README shows, rendered from the BPMN models                 |

`WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every blueprint - copy them
unchanged.

## Adding this blueprint to an existing project

1. Build `module-multi` first, or start from a project which already has two workflow
   modules. Everything about having two of them is that blueprint's subject.
2. Create a JAR both modules depend on and put two things in it: an interface naming what one
   module wants from the other, in the words of the business, and a record carrying the
   answer. Nothing else belongs in there - no aggregate, no BPMN, no `ProcessService`, no
   entity, no task handler.
3. Let the answering module's business service implement that interface. Its method starts
   whatever answers the request; that this is a process of its own is not part of the
   contract.
4. Publish the answer as an event, from a task of the answering process, so the model shows
   where the module speaks. Never call the asking module back: a module which knows its
   callers cannot be deployed without them.
5. In the asking module, call the interface from a `@WorkflowTask` method like any other
   business call, and model the waiting as a message event. Add a listener which takes the
   event and hands it to the business code, which correlates the message. The listener is a
   driving adapter and belongs next to the REST controller, not into `Workflow`.
6. Keep the case id as the thing that crosses: the asking module's aggregate id, a string.
   The answering module stores it as its own aggregate's id, which is what makes the answer
   findable without either module knowing the other's data model.
7. Do not model a message flow between the two processes, and do not let one process
   correlate a message into the other. That is one process in two files, and it breaks the
   moment one of them is remodelled.
8. Set the test profiles for the whole Maven module rather than per test class. Spring caches
   one application context per set of properties, and two contexts in one JVM mean two
   embedded engines competing for the jobs of one database - a test which starts a workflow
   then waits forever.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure.

```bash
mvn install verify -Pcamunda8
```

`-Pcamunda8` needs a running cluster and `vanillabp.adapters.camunda8.rest-address`
configured; do not report a failure of that profile as a defect of the generated code before
having checked it.

All tests have to pass, and two of them carry the aspect: `ModuleInteractionIT`, where the
answer of one module reaches the waiting process of the other, and `LoanApprovalIT`, where
the asking module runs with the other one stood in for by a stub. A stub which needs more
than a handful of lines means the two modules are coupled more tightly than they look.

Do not report success without having run this.
