# <blueprint-id>

<!--
Template for the AGENTS.md of a blueprint. This file is for AI AGENTS. It says what to replace,
which files carry the aspect, how to add it to an existing project, and how to check the
result. Explanations for humans belong in README.md. Write it in the plain English the section
"How we write" of the monorepo's AGENTS.md asks for.

bin/check_docs_structure.py checks the '##' headings below. Keep them word for word, in this
order. Remove these comments in a real blueprint.

Keep it short. The rules for all blueprints are in
https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md. Do not repeat
them here.

Never mention the other platform.

Code quoted here uses the names of the blueprint's code. Injected beans are named after their
role, as CONTRIBUTING.md describes: `loanApproval` for the `Service`, `bpms` for the
`ProcessService`.
-->

<One sentence: what this blueprint adds to an application.>

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint.

|        Placeholder         |                                      Meaning                                       |
|----------------------------|------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                       |
| `loanapproval`             | use case identifier, Java package                                                  |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path |
| `loan_approval`            | BPMN process ID                                                                    |

<Add placeholders of this blueprint here, for example the name of a BPMN task, and say where
each occurs.>

## Core files

The files carrying the aspect. Read these, adapt them, keep their structure.

|                     File                     |                Why it matters                |
|----------------------------------------------|----------------------------------------------|
| `src/main/resources/.../loan_approval.bpmn`  | <what the model contains>                    |
| `src/main/java/.../WorkflowTaskHandler.java` | <the @WorkflowTask methods and what they do> |
| `src/main/java/.../Service.java`             | <what the business code does with them>      |
| `src/main/java/.../Workflow.java`            | <what the application tells the process>     |
| `src/main/java/.../model/Aggregate.java`     | <the state the process needs>                |
| `src/test/java/.../<...>IT.java`             | <the test proving the aspect>                |

## Boilerplate files

They are here so that the blueprint runs on its own. Copy them unchanged, or use what the
target project already has.

|                  File                  |                       Purpose                       |
|----------------------------------------|-----------------------------------------------------|
| `pom.xml`                              | dependencies, the BPMS profiles, the platform build |
| `src/main/resources/application.yaml`  | datasource and VanillaBP configuration              |
| `src/main/java/.../ApiController.java` | the GET endpoints operating the process             |
| `docs/<process-id>.png`                | the picture of the process the README shows         |

## Adding this blueprint to an existing project

<Numbered steps, precise enough to follow without reading the README. Name the target file for
every step. Say what to do if the project already has that file.>

1. Add the BPMN model to the workflow module's resource directory.
2. Add the `@WorkflowTask` methods to `WorkflowTaskHandler`, the business methods they call
   to `Service`, and whatever the process has to be told to `Workflow`.
3. Extend the workflow aggregate by the attributes the process needs.
4. Add the API endpoints continuing the process.
5. Copy the integration test and adapt it to the use case.

## Verifying

```bash
mvn verify
```

<Name what has to be true afterwards: which test proves the aspect, and which startup message
appears when the BPMN and the code do not match. Do not report success without having run
this.>
