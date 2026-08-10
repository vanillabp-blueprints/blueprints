# <blueprint-id>

<!--
Template for the AGENTS.md of a blueprint. This file is for AI AGENTS: it says what to
replace, which files carry the aspect, how to graft it onto an existing project and how
to verify the result. Explanations for humans belong into README.md.

The '##' headings below are checked by bin/check_docs_structure.py - keep them verbatim,
in this order. Remove these comments in a real blueprint.

Keep it short. The rules valid for all blueprints are in
https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md
and must not be repeated here.

Never mention the other platform.
-->

<One sentence: what this blueprint adds to an application.>

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first — it carries the procedure, the reference structure and the list of things never to
do.

## Placeholders

Replace all of these consistently; they are the same in every blueprint.

|        Placeholder         |                                      Meaning                                       |
|----------------------------|------------------------------------------------------------------------------------|
| `blueprint.workflowmodule` | base package                                                                       |
| `loanapproval`             | use case identifier, Java package                                                  |
| `loan-approval`            | use case identifier, kebab case: workflow module ID, resource directory, REST path |
| `loan_approval`            | BPMN process ID                                                                    |

<Add blueprint-specific placeholders here, e.g. the name of a BPMN task, and say where
each occurs.>

## Core files

The files carrying the aspect. Read these, adapt them, keep their structure.

|                    File                     |                Why it matters                |
|---------------------------------------------|----------------------------------------------|
| `src/main/resources/.../loan_approval.bpmn` | <what the model contains>                    |
| `src/main/java/.../Workflow.java`           | <the @WorkflowTask methods and what they do> |
| `src/main/java/.../Service.java`            | <what the business code does with them>      |
| `src/main/java/.../model/Aggregate.java`    | <the state the process needs>                |
| `src/test/java/.../<...>IT.java`            | <the test proving the aspect>                |

## Boilerplate files

Present so the blueprint runs on its own. Copy them unchanged or use what the target
project already has.

|                  File                  |                       Purpose                       |
|----------------------------------------|-----------------------------------------------------|
| `pom.xml`                              | dependencies, the BPMS profiles, the platform build |
| `src/main/resources/application.yaml`  | datasource and VanillaBP configuration              |
| `src/main/java/.../ApiController.java` | the GET endpoints operating the process             |

## Adding this blueprint to an existing project

<Numbered steps, precise enough to be followed without reading the README. Name the target
file for every step and say what to do if the project already has that file.>

1. Add the BPMN model to the workflow module's resource directory.
2. Add the `@WorkflowTask` methods to `Workflow` of the use case, and the business
   methods calling them to `Service`.
3. Extend the workflow aggregate by the attributes the process needs.
4. Add the API endpoints continuing the process.
5. Copy the integration test and adapt it to the use case.

## Verifying

```bash
mvn verify
```

<Name what has to be true afterwards: which test proves the aspect, and which startup
message appears when the BPMN and the code do not match. Do not report success without
having run this.>
