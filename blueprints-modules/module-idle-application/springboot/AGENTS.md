# module-idle-application

Makes an application stop asking its database for work while a workflow waits in a timer:
two configuration keys, and an endpoint which counts what the database did so nobody has to
take the saving on trust. A delta on top of `module-single`.

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

|     Name     |                                         Where it occurs                                         |
|--------------|-------------------------------------------------------------------------------------------------|
| `payOutLoan` | the `@WorkflowTask` method behind the timer and the task definition of that service task        |
| `camunda7`   | the adapter id the engine keys are written under, which is the id of your own Camunda 7 adapter |

## Core files

|                                            File                                            |                                          Why it matters                                           |
|--------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the timer catch event the workflow waits in, and the service task behind it                       |
| `application/src/main/resources/application.yaml`                                          | `vanillabp.outbox.poll-interval`, the longest an outbox poller may rest while nothing is owed     |
| `application/src/main/resources/application-camunda7.yaml`                                 | `sleep-until-something-is-due`, which stops the embedded engine from asking every 5 to 60 seconds |
| `loan-approval/src/main/java/.../loanapproval/DatabaseTraffic.java`                        | counts the statements H2 wrote into its trace file, which is how the saving is seen               |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the endpoint returning that number                                                                |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | the proof: no statement at all while the workflow waits, and the workflow goes on afterwards      |

## Boilerplate files

|                                File                                 |                             Purpose                             |
|---------------------------------------------------------------------|-----------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles and the VanillaBP BOM import                  |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support`, never an adapter               |
| `application/pom.xml`                                               | the BPMS adapter, the only place a BPMS is named                |
| `application/src/main/java/.../Application.java`                    | the application class, in the parent package of the module      |
| `loan-approval/src/main/java/.../loanapproval/Service.java`         | the business methods the two tasks call                         |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java` | `paidOut`, the attribute the task behind the timer writes       |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                 |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring  |
| `docs/loan_approval.png`                                            | the picture of the process the README shows                     |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Put `vanillabp.outbox.poll-interval` into `application.yaml` and give it a value which
   matches your deployment. The poller waits for the earliest entry which is owed something
   and this key bounds that wait, so it is how long an entry another node wrote down before
   it went away may lie there. It is not a notification between nodes, and reading it as one
   is what makes people set it to seconds and give the whole saving back.
2. Put `sleep-until-something-is-due: true` under the adapter id of your embedded Camunda 7
   engine, in that engine's profile file. Leave it out for a remote BPMS, where the polling
   happens in the cluster rather than in your database.
3. Read the startup messages once. They name what is still awake in your application, and
   the engine one asks for a database index on the due date of `ACT_RU_JOB`, which only an
   operator can create.
4. Model the waiting as a timer in the process rather than as a scheduler in the
   application. A scheduler loses its state on a restart, a due date does not.
5. Take `DatabaseTraffic` and the endpoint calling it only if you want to see the numbers.
   They are measuring equipment, not application code: the JDBC URL has H2 write every
   statement into a file and the class counts its lines. On another database it is that
   database's own way of writing statements down.
6. Copy `LoanApprovalIT` and keep the shape of its assertion: measure what asking costs,
   then measure a window in which nothing may happen, then assert that the workflow went on
   anyway.

Never take the quiet half without the second assertion. An application which sleeps through
its own timers looks exactly like one which saves money, and the test is what tells the two
apart.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass:

- the database executes nothing while the workflow waits in its timer, apart from the two
  questions the test itself asks,
- the loan is paid out after the timer is due, which is the workflow going on.

A test which fails on the first assertion means a poller is still running: check that both
keys arrived, and that the profile file carrying the engine key is loaded by the profile the
test runs in. A test which fails on the second means the engine was left waiting, which is
the serious one.

Do not report success without having run this.
