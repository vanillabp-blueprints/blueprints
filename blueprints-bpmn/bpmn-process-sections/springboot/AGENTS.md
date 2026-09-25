# bpmn-process-sections

Cuts a process into two sections and gives each of them a sub-object of the workflow
aggregate. One section is skipped for part of the workflows, so its sub-object stays absent,
and the model asks about it through a boolean getter on the top level. A delta on top of
`module-single`.

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
| `loan_approval`            | BPMN process ID of the process the application starts                                                                     |

Blueprint-specific names, each occurring in more than one place:

|                     Name                     |                                                              Where it occurs                                                              |
|----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `document_check`                             | the ID of the called process, the file name of its BPMN, `calledElement` respectively `zeebe:calledElement`, and `secondaryBpmnProcesses` |
| `documentCheck`, `riskAssessment`            | the attribute of a section on the aggregate, its class, and every getter reading into it                                                  |
| `riskAssessed`                               | the getter answering whether the second section ran, and the condition of the gateway after both paths                                    |
| `signaturesComplete`, `collateralSufficient` | a finding of a section, read by the model of that section                                                                                 |

## Core files

The files carrying the aspect. Read these, adapt them, keep their structure.

|                                            File                                             |                                          Why it matters                                           |
|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                         | one sub-object per section, and the boolean getters the models read, each with its null check     |
| `loan-approval/src/main/java/.../loanapproval/model/RiskAssessment.java`                    | the section object which is absent when the section did not run                                   |
| `loan-approval/src/main/java/.../loanapproval/model/DocumentCheck.java`                     | the section object of the section every workflow runs through                                     |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn`  | the embedded subprocess, the call activity, and the gateway asking whether the second section ran |
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/document_check.bpmn` | the called process of the first section, reading its own finding inside itself                    |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                 | the first task of a section creates that section's object, the later ones fill it in              |
| `loan-approval/src/main/java/.../loanapproval/WorkflowTaskHandler.java`                     | one `@WorkflowService` naming both processes, and the `@WorkflowTask` methods of both sections    |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                       | one run per path, and the getters asked on an aggregate which reached no section                  |

## Boilerplate files

Present so the blueprint runs on its own. Copy them unchanged or use what the target
project already has.

|                                File                                 |                                      Purpose                                      |
|---------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                          | the BPMS profiles and the VanillaBP BOM import                                    |
| `loan-approval/pom.xml`                                             | `vanillabp-spring-boot-support`, never an adapter                                 |
| `application/pom.xml`                                               | the BPMS adapter, the only place a BPMS is named                                  |
| `application/src/main/java/.../Application.java`                    | the Spring Boot application, in the parent package of the module                  |
| `application/src/main/resources/application.yaml`                   | the datasource, and the profile file of the engine in use                         |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`   | GET endpoints operating the process                                               |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`        | starts the process; a called process is never started from code                   |
| `loan-approval/src/main/resources/loan-approval/loan-approval.yaml` | the limit which makes the second section run, and the numbers the sections use    |
| `loan-approval/src/test/java/.../TestApplication.java`              | the minimal application the module's test boots                                   |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`           | base class of the integration test: waits for workflow progress                   |
| `application/src/test/java/.../ApplicationSmokeTest.java`           | boots the application, which validates the BPMN-to-code wiring                    |
| `docs/loan_approval.png`, `docs/document_check.png`                 | the pictures of the two processes the README shows, rendered from the BPMN models |

`TestApplication`, `WorkflowModuleTest` and `ApplicationSmokeTest` are identical in every
blueprint - copy them unchanged.

## Adding this blueprint to an existing project

1. Decide where the sections are. A section is a part of the process whose steps belong
   together. Draw it as an embedded subprocess when it stays inside this model, and as a call
   activity when it deserves a model of its own.
2. Give every section a class of its own and hold it as one attribute on the workflow
   aggregate. Do not spread a section's findings over the top level of the aggregate, and do
   not give a section a workflow aggregate of its own.
3. Let the first task of a section create that object and the later tasks fill it in. Before
   that first task the attribute is absent, and for a section the process skips it stays
   absent until the workflow ends.
4. **Never let a BPMN expression read a path into a section object**, so no
   `${riskAssessment.collateralSufficient}` and no entry of that shape in the sync
   configuration. For a workflow which skipped the section the path does not resolve, and
   VanillaBP stops that workflow naming the path and the part of it which is empty.
5. Write one getter on the aggregate per question the model asks. Return `boolean`, not
   `Boolean`, and check the section object for null in the first line. A question about
   whether a section ran is `sectionObject != null` and nothing more.
6. Where a model really needs a value which only exists after a section, and a getter cannot
   answer it, declare a substitute for that path in the configuration of the workflow. That
   is a decision somebody writes down rather than a value the framework invents.
7. A model inside a section may read that section's findings. Keep the null check in the
   getter anyway: one aggregate serves every model of the workflow module.
8. Extend the integration test by one run per path through the sections, and by one case
   which asks every shared getter on an aggregate that reached no section at all.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass. The run which matters most is the one
below the limit: it goes through to a decision while the second section never ran, and a
workflow which stops instead is the mistake this blueprint exists for. A BPMN expression
reading into a section object fails while the workflow runs, not while the application
starts, so the test is the only thing that finds it.

Do not report success without having run this.
