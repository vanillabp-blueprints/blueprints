# bpmn-message-correlation

Adds a message which a running workflow waits for. The application writes what the message
carried onto the workflow aggregate, and then correlates it by that aggregate. A delta on top
of `module-single`.

Read
[the organisation-wide AGENTS.md](https://raw.githubusercontent.com/vanillabp-blueprints/.github/main/AGENTS.md)
first. It has the procedure, the reference structure and the list of things never to do.

## Placeholders

Replace all of these consistently. They are the same in every blueprint, except the Maven
coordinates, which carry the name of the blueprint.

|                         Placeholder                          |                                                                                 Meaning                                                                                 |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `blueprint.workflowmodule`                                   | base package                                                                                                                                                            |
| `blueprint.application`                                      | package of the application, not above the workflow module                                                                                                               |
| `loanapproval`                                               | use case identifier, Java package                                                                                                                                       |
| `loan-approval`                                              | use case identifier, kebab case: workflow module ID, resource directory, REST path, Maven module, configuration file name                                               |
| `loan_approval`                                              | BPMN process ID                                                                                                                                                         |
| `LoanApproval`                                               | use case identifier, CamelCase: the start of class names, for example `LoanApprovalProperties`, `LoanApprovalIT` or the interface `LoanApprovalApi` the build generates |
| `LOAN_APPROVAL`                                              | use case identifier, upper snake case: table and collection names, and the start of other names in upper case                                                           |
| `io.vanillabp.blueprint.bpmn-message-correlation-springboot` | Maven groupId of every POM of this blueprint                                                                                                                            |
| `bpmn-message-correlation`                                   | Maven artifactId of the root POM                                                                                                                                        |

Blueprint-specific names, each occurring in more than one place:

|       Name       |                                     Where it occurs                                      |
|------------------|------------------------------------------------------------------------------------------|
| `ContractSigned` | the constant `Workflow.CONTRACT_SIGNED` and the `bpmn:message` name in the model         |
| `payOut`         | the `@WorkflowTask` method behind the message event and the task definition of that task |

The message name is the contract between code and model. If the two drift apart, the
correlation reaches no waiting event. The call then fails at runtime, not at startup.

## Core files

|                                            File                                            |                                                       Why it matters                                                       |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/<adapter-id>/loan_approval.bpmn` | the message catch event and the `bpmn:message` it references. NOTHING about the correlation itself                         |
| `loan-approval/src/main/java/.../loanapproval/Workflow.java`                               | `correlateMessage(aggregate, messageName)` and the message name as a constant                                              |
| `loan-approval/src/main/java/.../loanapproval/Service.java`                                | writes the message's content onto the aggregate BEFORE correlating, and returns early on a repeat                          |
| `loan-approval/src/main/java/.../loanapproval/model/Aggregate.java`                        | the attributes the message brings. The payload stops here                                                                  |
| `loan-approval/src/main/java/.../loanapproval/ApiController.java`                          | the endpoint the message arrives at                                                                                        |
| `loan-approval/src/main/resources/loan-approval/openapi/loan-approval.yaml`                | the API as an OpenAPI file. The build generates the interface `LoanApprovalApi` from it, and `ApiController` implements it |
| `loan-approval/src/test/java/.../LoanApprovalIT.java`                                      | waiting, continuing, and a message arriving twice                                                                          |

## Boilerplate files

|                                    File                                     |                                                                            Purpose                                                                             |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pom.xml` (blueprint root)                                                  | the BPMS profiles and the VanillaBP BOM import                                                                                                                 |
| `loan-approval/pom.xml`                                                     | `vanillabp-spring-boot-support`, never an adapter                                                                                                              |
| `application/pom.xml`                                                       | the BPMS adapter, the only place a BPMS is named                                                                                                               |
| `application/src/main/java/.../Application.java`                            | the Spring Boot application, in `blueprint.application`, which is not above the module. It names nothing of the module                                         |
| `loan-approval/src/main/java/.../config/LoanApprovalAutoConfiguration.java` | the module wires itself: a component scan of its own package, registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `application/src/main/resources/application.yaml`                           | the datasource, and the profile the Maven build filters in                                                                                                     |
| `application/src/main/resources/application-camunda7.yaml`                  | the Camunda 7 settings: the name-clash-avoidance mode and the demo user of its web applications                                                                |
| `loan-approval/src/test/java/.../TestApplication.java`                      | the minimal application the module's test boots. It scans nothing and gets the module from its auto-configuration                                              |
| `loan-approval/src/test/java/.../WorkflowModuleTest.java`                   | base class of the integration test: waits for workflow progress                                                                                                |
| `application/src/test/java/.../ApplicationSmokeTest.java`                   | boots the application, which validates the BPMN-to-code wiring                                                                                                 |
| `docs/loan_approval.png`                                                    | the picture of the process the README shows, rendered from the BPMN model                                                                                      |

## Adding this blueprint to an existing project

1. Add the message catch event to the BPMN and declare a `bpmn:message` for it. Do NOT model a
   correlation key, a business key or an expression. VanillaBP correlates by the workflow
   aggregate's id. Each adapter arranges what its engine needs for that while deploying.
2. Add the attributes the message brings to the workflow aggregate. **The content of a message
   never travels to the BPMS (business process management system).** So anything the process or
   a later step may need has to be there.
3. Add a method to `Service` which loads the aggregate, writes those attributes, and only then
   calls `Workflow`. It runs in a transaction, because `Service` says `@Transactional` on the
   class. The aggregate is saved with the correlation. On a remote BPMS the correlation is sent
   after the commit.
4. Decide what a second delivery of the same message means, and implement that decision in
   `Service`. Without a correlation id VanillaBP does not drop repeats, on purpose. The same
   message may arrive more than once, and that can be correct. The usual answer is an early
   return, decided by the state of the aggregate.
5. Add the `correlateMessage` call to `Workflow` and keep the message name there as a
   constant.
6. Add the endpoint the message arrives at. When the workflow starts waiting, log the URL which
   continues the process.
7. Copy `LoanApprovalIT`: one test for the waiting workflow, one for the message which lets it
   continue, one for a repeat.

The sender may not know the aggregate's id. A delivery note, for example, carries the order
number of the supplier, not the id of your purchase request. Then store that key on the
aggregate as soon as you learn it, add a lookup by it to `AggregateRepository`, and let the
method in `Service` load the aggregate by that key. It must find exactly one aggregate. Then
correlate by the aggregate as above. Nothing changes in the model. A correlation id does not
help here. It tells several waiting occurrences of the same message apart inside one workflow,
it does not find the workflow.

A message may arrive while nothing waits for it: before the workflow reached the catch event,
or while it is between two waits, for example in a loop which leads back to an event-based
gateway. Without a time-to-live the Camunda 7 family refuses such a correlation at your call,
and your transaction fails. Camunda 8 keeps the message for its time-to-live. If your message
can come at such a moment, set `message-time-to-live` for the workflow module in
`loan-approval.yaml`:

```yaml
vanillabp:
  workflow-modules:
    loan-approval:
      message-time-to-live: 10m
```

Then the message waits that long for the workflow on every BPMS which supports it. This
blueprint sets none. A person opens its message URL by hand, long after the workflow reached
the catch event. The
[wiki](https://github.com/vanillabp/adapter-platform-integration/wiki/Message-correlation#a-message-which-comes-before-the-workflow-waits-for-it)
explains the levels of the key and what happens when the time is over.

If several occurrences of the same message may wait at once, for example one per ordered item,
pass a correlation id as the third argument. That variant drops repeats among the correlations
which still wait for their dispatch. So a repeating scope has to vary the id per round or
element. What the model needs for it is specific to the BPMS.

## Verifying

```bash
mvn install verify
```

That runs on Camunda 7, which is embedded and needs no infrastructure. `-Pcamunda8` needs a
running cluster and a configured `vanillabp.adapters.camunda8.rest-address`. Check both before
you report a failure of that profile as a defect of the generated code.

`LoanApprovalIT` proves the aspect and has to pass:

- before the message, the service task behind the catch event has NOT run. This proves that the
  workflow waits,
- after correlating, it has run, and what the message carried is on the aggregate,
- a second message changes nothing.

A test which correlates a message right after the workflow started has to accept that a remote
engine does not know the workflow yet. It answers such lookups from an index it fills in the
background. That is why `LoanApprovalIT` retries. An embedded engine does not need the retry.

Do not report success without having run this.
