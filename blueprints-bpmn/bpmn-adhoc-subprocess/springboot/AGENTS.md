# bpmn-adhoc-subprocess

Adds an ad-hoc subprocess: activities drawn side by side without sequence flows, of which the
workflow runs the ones somebody picked. Two decision makers are shown, a case worker answering
a user task and a decision table, because the element does not know which of them filled the
list. A delta on top of `module-single`, Camunda 8 only.

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

|                       Name                        |                                                Where it occurs                                                |
|---------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `Check_Income`, `Check_Fraud`, `Check_Collateral` | the ids of the service tasks inside the ad-hoc subprocess, the enum `Check`, and the output column of the DMN |
| `checksToRun`                                     | the aggregate attribute, the `activeElementsCollection` of the model and the `resultVariable` of the DMN      |
| `selectChecks`                                    | the `@WorkflowTask` method and the `zeebe:formDefinition externalReference` of the user task                  |
| `checks_to_run`                                   | the `decisionId` of the DMN and the `zeebe:calledDecision` of the business rule task                          |

The element ids are the part which is easy to get wrong. The model activates elements by their
id, so a check renamed in the BPMN and not in `Check.java` leaves the workflow standing in the
subprocess with nothing to do, and the same holds for the output column of the decision table.
The ids are not touched by name-clash avoidance, so they are the same string everywhere.

## Core files

|                                          File                                          |                                                             Why it matters                                                             |
|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/camunda8/loan_approval.bpmn` | the ad-hoc subprocess with `activeElementsCollection`, three service tasks inside it, a user task and a business rule task ahead of it |
| `loan-approval/src/main/resources/loan-approval/processes/camunda8/loan_approval.dmn`  | the decision table, hit policy COLLECT, whose output column holds BPMN element ids                                                     |
| `loan-approval/src/main/java/.../loanapproval/model/Check.java`                        | the checks and their element ids, the one place the contract with the model is written down                                            |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                    | `checksToRun`, the version attribute the concurrent checks need, and one flag per check                                                |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                | one `@WorkflowTask` method per check, and NO method for the ad-hoc subprocess itself                                                   |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                            | the checks as business methods, and the two halves of the user task picking them                                                       |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                           | `completeUserTask`, and nothing about the subprocess: the choice travels on the aggregate                                              |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                      | the GET endpoint carrying the task id and the names of the checks                                                                      |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                  | one test per decision maker                                                                                                            |

## Boilerplate files

|                            File                            |                                  Purpose                                   |
|------------------------------------------------------------|----------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                 | the BPMS profile, the VanillaBP BOM import and the BPMS name for the tests |
| `loan-approval/pom.xml`                                    | `vanillabp-spring-boot-support`, never an adapter                          |
| `application/pom.xml`                                      | the BPMS adapter, the only place a BPMS is named                           |
| `application/src/main/java/.../Application.java`           | the Spring Boot application, in the parent package of the module           |
| `application/src/main/resources/application.yaml`          | the datasource and the profile the Maven build filters in                  |
| `application/src/main/resources/application-camunda8.yaml` | the cluster address and the name-clash-avoidance mode                      |
| `loan-approval/src/test/java/.../TestApplication.java`     | the minimal application the module's test boots                            |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`  | base class of the integration test: waits for workflow progress            |
| `application/src/test/java/.../ApplicationSmokeTest.java`  | boots the application, which validates the BPMN-to-code wiring             |
| `docs/loan_approval.png`                                   | the picture of the process the README shows, rendered from the BPMN model  |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Draw the ad-hoc subprocess and put the activities inside it, each one an ordinary task with
   its own task definition. Set *Active elements collection* to a FEEL expression naming an
   attribute of your workflow aggregate, `=checksToRun` here. Do not set a task definition on
   the subprocess itself: that is the other flavour of the element, where a worker decides
   round by round which activities to activate, and VanillaBP does not serve it.
2. Add a `List<String>` attribute to the workflow aggregate. VanillaBP shares the aggregate
   with the BPMS on every command and a collection travels as a list, so nothing else is
   needed to get the choice into the model.
3. Write down the element ids once, as an enum or as constants. Everything else in the
   application speaks about a check by its name, and only that one place knows which BPMN
   element each of them is.
4. Add a `@WorkflowTask` method per activity inside the element, exactly as for a task on a
   sequence flow. Add none for the subprocess.
5. Decide where the list comes from. A user task plus `completeUserTask` puts a person in
   charge; a business rule task whose `resultVariable` is the same name puts a decision table
   in charge and needs no Java at all. Both can sit in one model behind a gateway.
6. Give the aggregate a version attribute if more than one activity can run at a time. Two
   activated activities are two branches writing the same row, and without it the branch
   committing second silently overwrites the other.
7. Copy `LoanApprovalIT` and write one test per way the list is filled.

A completion condition is optional. Without one the workflow leaves the element when
everything it activated is done, which is what this blueprint shows.

## Verifying

```bash
mvn install verify
```

That needs a running Camunda 8 cluster and `vanillabp.adapters.camunda8.rest-address`
configured; do not report a failure as a defect of the generated code before having checked
that. Camunda 7 is not an option here, because it does not execute the element.

`LoanApprovalIT` proves the aspect and has to pass:

- the case worker picks two of the three checks, those two ran, the third did not, and the
  service task behind the subprocess ran, which is what says the workflow left the element,
- the decision table picks for a request nobody looked at, and the aggregate's own list stayed
  empty because no Java was involved on that path.

If a check never runs, the element id in the code or in the decision table does not match the
BPMN. If the workflow stops inside the subprocess, the list named an id which is not an element
of it. If a check runs but its result is gone, the aggregate lost the version attribute and the
two branches overwrote each other.

Do not report success without having run this.
