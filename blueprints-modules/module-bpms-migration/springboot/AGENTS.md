# module-bpms-migration

Two BPMS (business process management system) adapters configured at once. New workflows start
in the new BPMS, and workflows started earlier are completed in the old one. A delta on top of
`module-single`.

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

|                               Name                               |                                     Where it occurs                                      |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `camunda7`, `camunda8`                                           | the two adapter ids: the priority list, the adapter configuration and the Maven profiles |
| `loan_repayment`                                                 | the second BPMN process and the workflow-level priority list naming the old BPMS         |
| `ContractSigned`                                                 | the message name in the model and the constant passed to `correlateMessage`              |
| `assessRisk`, `retrieveCreditRating`, `payOut`, `bookInstalment` | one `@WorkflowTask` method and one task definition each                                  |

You can choose the adapter ids freely. These two are chosen so that the CI can pass the address
of its cluster to `vanillabp.adapters.camunda8.rest-address` through the environment. In a
project they could just as well be `old-bpms` and `new-bpms`, which reads better in a priority
list.

## Core files

|                              File                              |                                                         Why it matters                                                          |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `application/src/main/resources/application-camunda8.yaml`     | the migration state: `prioritized-adapters`, both adapters with the same scoping mode, the workflow-level list of the repayment |
| `application/src/main/resources/application-camunda7.yaml`     | the state before it: one adapter, nothing to prioritize, and the demo user of its web applications                              |
| `loan-approval/src/test/resources/application-camunda7.yaml`   | the same state for the module's own test, with the same scoping mode, so the test runs what the application ships               |
| `loan-approval/.../processes/<adapter-id>/loan_approval.bpmn`  | a user task and a message catch event: two operations which have to find the BPMS holding the workflow                          |
| `loan-approval/.../processes/<adapter-id>/loan_repayment.bpmn` | the second workflow, the one which stays behind                                                                                 |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`   | `startWorkflow`, `completeUserTask`, `correlateMessage`, and the viewer call naming the adapter                                 |
| `loan-approval/src/main/java/.../loanapproval/Service.java`    | the business code, and the one method reading which BPMS holds a workflow                                                       |
| `loan-approval/src/main/java/.../loanrepayment/`               | the second use case. Its classes are named after it, so two use cases of one module stay apart                                  |
| `loan-approval/src/test/java/.../MigrationIT.java`             | where a workflow starts, where a pinned one starts, and that both wait states are answered                                      |
| `application/src/test/java/.../MigrationRestartIT.java`        | the migration itself: two boots against one file database                                                                       |

## Boilerplate files

|                           File                            |                                    Purpose                                     |
|-----------------------------------------------------------|--------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                | the BPMS profiles and the VanillaBP BOM import                                 |
| `loan-approval/pom.xml`                                   | `vanillabp-spring-boot-support`, never an adapter                              |
| `application/pom.xml`                                     | the BPMS adapter, the only place a BPMS is named                               |
| `application/src/main/java/.../Application.java`          | the Spring Boot application, in the parent package of the module               |
| `application/src/main/resources/application.yaml`         | the file database both boots share, and the profile the Maven build filters in |
| `loan-approval/src/test/java/.../TestApplication.java`    | the minimal application the module's test boots                                |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java` | base class of the integration test: waits for workflow progress                |
| `application/src/test/java/.../ApplicationSmokeTest.java` | boots the application, which validates the BPMN-to-code wiring                 |
| `docs/loan_approval.png`                                  | the picture of the process the README shows, rendered from the BPMN model      |

## Adding this blueprint to an existing project

1. Configure the BPMS you have today as an adapter instance under `vanillabp.adapters.<id>`.
   Choose the id yourself. One adapter needs no priority list.
2. Add the second adapter as a dependency and configure it the same way. From now on the order
   matters, so name it in `vanillabp.prioritized-adapters`: the new BPMS first, the old one
   after it. **New workflows start in the first entry, and a start asks no adapter.** Give both
   adapters the same `name-clash-avoidance` mode. Repeat it in every profile or file your
   configuration is split into. The mode decides what an engine calls a process, a message, a
   signal and an error. So changing it during the migration renames what the old BPMS already
   holds, and nothing finds the workflows there any more. Leaving it out of one place is enough
   for that, because the default is `by-adapter`. Changing the mode is a migration of its own,
   with a second adapter id for the same engine.
3. Deploy nothing by hand. The BPMN models of a workflow module go to every adapter its lists
   name. So both engines are ready for their workflows.
4. Change no business code. Completing a task, completing a user task and correlating a message
   go to the adapter which holds the workflow. VanillaBP finds it by asking the adapters in the
   order of the list.
5. Migrate one workflow at a time where you want to. The same key exists per workflow module
   (`vanillabp.workflow-modules.<module>.prioritized-adapters`) and per workflow
   (`...workflows.<bpmn-process-id>.prioritized-adapters`). The most specific list which is not
   empty wins.
6. Keep the old adapter configured until none of its workflows is left. If you remove it while
   a workflow still lives there, every operation on that workflow fails. The message says that
   no adapter knows it.
7. Two adapter ids of the same EMBEDDED engine are two engines and must not share one set of
   engine tables. Give one of them a table prefix or a data source of its own.

What to expect while testing against a remote BPMS: it may not know a workflow started moments
ago for a few seconds. It answers from a read model which catches up after a delay. VanillaBP
keeps asking the adapter it recorded for as long as that adapter says it may need. So do not
write code around this case.

Do not write an "adapter" parameter into the business code. Do not look for an API which moves
a running workflow from one BPMS to another. There is none, and that is the point: the
workflows already running finish where they are.

## Verifying

```bash
mvn install verify
```

That runs against the embedded engine alone, which is the state before a migration. The
migration state is `-Pcamunda8`. It brings BOTH adapters and needs a running cluster plus
`vanillabp.adapters.camunda8.rest-address`.

`MigrationIT` is the proof, and it has to pass with both adapters configured. A loan approval
starts in the new BPMS. A repayment starts in the old one, because its own list says so. The
BPMS which holds a workflow answers its user task and its message. Under the other profile
those tests skip themselves, because with one BPMS there is nothing to route.

`MigrationRestartIT` is the second proof. It boots the application with the old BPMS alone and
starts two workflows. Then it boots again with both adapters and finishes those two together
with a new one. If it fails while `MigrationIT` passes, the routing works but something about
the restart does not. Usually that is the database or a context left running.

The database is a FILE here, unlike in every other blueprint, because the scenario restarts the
application. A test which has to start from nothing deletes it first. `mvn clean` throws it
away.

Do not report success without having run this.
