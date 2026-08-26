# bpmn-message-correlation

Adds a message a running workflow waits for: the application writes what the message
carried onto the workflow aggregate and then correlates it by that aggregate. A delta on
top of `module-single`.

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

|       Name       |                                     Where it occurs                                      |
|------------------|------------------------------------------------------------------------------------------|
| `ContractSigned` | the constant `Workflow.CONTRACT_SIGNED` and the `bpmn:message` name in the model         |
| `payOut`         | the `@WorkflowTask` method behind the message event and the task definition of that task |

The message name is the contract between code and model. If the two drift apart, the
correlation reaches no waiting event and the call fails at runtime, not at startup.

## Core files

|                                            File                                            |                                           Why it matters                                           |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the message catch event and the `bpmn:message` it references. NOTHING about the correlation itself |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `correlateMessage(aggregate, messageName)` and the message name as a constant                      |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | writes the message's content onto the aggregate BEFORE correlating, and returns early on a repeat  |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the attributes the message brings; the payload stops here                                          |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the endpoint the message arrives at                                                                |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | waiting, continuing, and a message arriving twice                                                  |

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

1. Add the message catch event to the BPMN and declare a `bpmn:message` for it. Do NOT
   model a correlation key, a business key or an expression: VanillaBP correlates by the
   workflow aggregate's id, and each adapter arranges what its engine needs for that while
   deploying.
2. Add the attributes the message brings to the workflow aggregate. **The content of a
   message never travels to the BPMS**, so anything the process or a later step may need has
   to be there.
3. Add a method to `Service` which loads the aggregate, writes those attributes and only
   then calls `Workflow`. Annotate it with `@Transactional`: the aggregate is saved with the
   correlation, and on a remote BPMS the correlation is sent after the commit.
4. Decide what a second delivery of the same message means, and implement that decision in
   `Service`. Without a correlation id VanillaBP does not deduplicate, on purpose - the same
   message may legitimately arrive more than once. An early return keyed on aggregate state
   is the usual answer.
5. Add the `correlateMessage` call to `Workflow` and keep the message name there as a
   constant.
6. Add the endpoint the message arrives at, and log the URL continuing the process when the
   workflow starts waiting.
7. Copy `LoanApprovalIT`: one test for the waiting workflow, one for the message letting it
   continue, one for a repeat.

If several occurrences of the same message may wait at once - one per ordered item, say -
pass a correlation id as the third argument. That variant deduplicates the correlations still
waiting for their dispatch, so a repeating scope has to vary the id per round or element, and what
the model needs for it is BPMS-specific.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and `vanillabp.adapters.camunda8.rest-address` configured; do not report a
failure of that profile as a defect of the generated code before having checked it.

`LoanApprovalIT` proves the aspect and has to pass:

- before the message the service task behind the catch event has NOT run, which is what
  proves the workflow waits,
- after correlating it has run, and what the message carried is on the aggregate,
- a second message changes nothing.

A test correlating a message right after the workflow started has to tolerate that a remote
engine does not know the workflow yet: it answers such lookups from an index it fills
asynchronously. `LoanApprovalIT` retries for that reason, and the retry is not needed on an
embedded engine.

Do not report success without having run this.
