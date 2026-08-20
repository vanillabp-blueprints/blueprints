# module-bpms-migration

Two BPMS adapters configured at once: new workflows start in the new BPMS while workflows
started earlier are completed in the old one. A delta on top of `module-single`.

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

|                               Name                               |                                     Where it occurs                                      |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `camunda7`, `camunda8`                                           | the two adapter ids: the priority list, the adapter configuration and the Maven profiles |
| `loan_repayment`                                                 | the second BPMN process and the workflow-level priority list naming the old BPMS         |
| `ContractSigned`                                                 | the message name in the model and the constant passed to `correlateMessage`              |
| `assessRisk`, `retrieveCreditRating`, `payOut`, `bookInstalment` | one `@WorkflowTask` method and one task definition each                                  |

The adapter ids are free, and these two are chosen so the CI can hand the address of its
cluster to `vanillabp.adapters.camunda8.rest-address` through the environment. In a project
they may as well be `old-bpms` and `new-bpms`, which reads better in a priority list.

## Core files

|                              File                              |                                             Why it matters                                             |
|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `application/src/main/resources/application-camunda8.yaml`     | the migration state: `prioritized-adapters`, both adapters, the workflow-level list of the repayment   |
| `application/src/main/resources/application-camunda7.yaml`     | the state before it: one adapter, and nothing to prioritize                                            |
| `loan-approval/.../processes/<adapter-id>/loan_approval.bpmn`  | a user task and a message catch event: two operations which have to find the BPMS holding the workflow |
| `loan-approval/.../processes/<adapter-id>/loan_repayment.bpmn` | the second workflow, the one which stays behind                                                        |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`   | `startWorkflow`, `completeUserTask`, `correlateMessage`, and the viewer call naming the adapter        |
| `loan-approval/src/main/java/.../loanapproval/Service.java`    | the business code, and the one method reading which BPMS holds a workflow                              |
| `loan-approval/src/main/java/.../loanrepayment/`               | the second use case, its classes named after it so two use cases of one module stay apart              |
| `loan-approval/src/test/java/.../MigrationIT.java`             | where a workflow starts, where a pinned one starts, and that both wait states are answered             |

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
| `docs/loan_approval.png`                                  | the picture of the process the README shows, rendered from the BPMN model            |

## Adding this blueprint to an existing project

1. Configure the BPMS you have today as an adapter instance under
   `vanillabp.adapters.<id>`, with an id of your own choosing. One adapter needs no priority
   list.
2. Add the second adapter as a dependency and configure it the same way. From now on the
   order matters, so name it: `vanillabp.prioritized-adapters`, the new BPMS first and the
   old one behind it. **New workflows start in the first entry, and no probing happens on a
   start.**
3. Deploy nothing by hand. The BPMN models of a workflow module go to every adapter its
   lists name, so both engines are ready for their workflows.
4. Change no business code. Completing a task, completing a user task and correlating a
   message are routed to the adapter holding the workflow, which VanillaBP finds by asking
   the adapters in the order of the list.
5. Migrate one workflow at a time where you want to: the same key exists per workflow module
   (`vanillabp.workflow-modules.<module>.prioritized-adapters`) and per workflow
   (`...workflows.<bpmn-process-id>.prioritized-adapters`), and the most specific non-empty
   list wins.
6. Keep the old adapter configured until no workflow of it is left. Removing it while a
   workflow still lives there makes every operation on that workflow fail, with a message
   saying that no adapter knows it.
7. Two adapter ids of the same EMBEDDED engine are two engines and must not share one set of
   engine tables. Give one of them a table prefix or a data source of its own.

What to expect while testing against a remote BPMS: a workflow started moments ago may be
unknown to it for a few seconds, because it answers from an eventually consistent read model.
VanillaBP keeps asking the adapter it recorded for as long as that adapter says it may need,
so this is not a case to code around.

Do not write an "adapter" parameter into the business code, and do not look for an API which
moves a running workflow from one BPMS to another. There is none, and that is the point: the
workflows already running finish where they are.

## Verifying

```bash
mvn install verify
```

That runs against the embedded engine alone, which is the state before a migration. The
migration state is `-Pcamunda8`: it brings BOTH adapters and needs a running cluster plus
`vanillabp.adapters.camunda8.rest-address`.

`MigrationIT` is the proof and has to pass with both adapters configured: a loan approval
starts in the new BPMS, a repayment starts in the old one because its own list says so, and
the user task and the message of a workflow are answered by the BPMS holding it. Under the
other profile those tests skip themselves, because with one BPMS there is nothing to route.

There is no restart test on this platform: a test here boots the application once, with the
configuration of the test. The README walks the restart through by hand, and the routing
itself is what `MigrationIT` proves.

The database is a FILE here, unlike in every other blueprint, because the scenario restarts
the application. A test which has to start from nothing deletes it first, and `mvn clean`
throws it away.

Do not report success without having run this.
