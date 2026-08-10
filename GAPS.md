# Gaps found while building blueprints

Cases where writing a blueprint ran into something `spi-for-java` or
`adapter-platform-integration` does not cover, or covers in a way a developer only finds
out about by debugging. From here they move into the framework roadmap as stories.

One section per finding. Keep the reproduction concrete enough to be turned into a test.

## G1: an application-declared `@Transactional` silently disables the `TaskException` contract

**Status:** open, found 2026-08-10 while building `bpmn-service-task/springboot`.

**Contract.** A `@WorkflowTask` method throwing a `TaskException` ends in a BPMN error, and
everything the handler wrote onto the workflow aggregate is committed. This is what makes a
BPMN error a business outcome rather than a failure, and the wiki states it as a contract
holding on every adapter and platform.

**What happens.** If the application puts `@Transactional` on the task handler, or on the
business method the handler calls, the Spring transaction interceptor marks the surrounding
transaction rollback-only when the `TaskException` passes it, because it is a
`RuntimeException` like any other. VanillaBP then commits a transaction that can no longer
commit anything, and the aggregate comes out of the database unchanged.

Nothing says so. No exception, no warning, no log line. The workflow takes the error path as
expected, so the model behaves correctly while the data does not, which is the worst
combination for finding it: the loan is rejected in the process and looks untouched in the
database.

**Reproduction.** In `bpmn-service-task/springboot`, add `@Transactional` to
`WorkflowTaskHandler` and run `LoanApprovalIT`:

```
LoanApprovalIT.aRejectedLoanTakesTheErrorPathAndKeepsWhatTheHandlerWrote
  The workflow '...' did not reach the expected state within PT30S.
  Last seen: Aggregate(loanRequestId=..., amount=500, creditRating=null, ratedBy=null, rejectionReason=null)
```

Without the annotation the same test passes, with `creditRating=5` and the rejection reason
persisted.

**Why it will be hit.** Putting `@Transactional` on a service class is the default reflex in
a Spring Boot application, and it is the right thing to do for every method the API calls.
VanillaBP 1 even required `@Transactional(noRollbackFor = TaskException.class)` on the
service, so anybody migrating brings the annotation along. The blueprints work around it by
declaring transactions per method and writing down why, but a convention documented in a
blueprint is not a safeguard.

**What VanillaBP should do**, in descending order of preference:

1. Handle it: run the task handler in a transaction VanillaBP fully controls, so that an
   application-declared `@Transactional` joining it cannot decide about the rollback. If a
   `TaskException` is on its way out, the rollback-only mark set by the interceptor has to be
   irrelevant.
2. Tell the developer at startup: the wiring analysis already inspects every
   `@WorkflowTask` method. A method carrying `@Transactional` on itself or on its declaring
   class is detectable there, and a startup message naming the class and what will happen
   costs nothing at runtime.
3. Tell the developer at runtime: when a `TaskException` is handled and the transaction
   turns out to be rollback-only, log an error naming the workflow, the task and the likely
   cause instead of committing nothing in silence.

Options 2 and 3 are not alternatives to each other. A startup check catches the common case
before anybody runs a process, and the runtime check catches what a startup check cannot
see, for example a transactional proxy somewhere further down the call chain.

**Affects:** every platform, since the mechanism is the platform's transaction interceptor
rather than the BPMS. Verified on Spring Boot with the Camunda 7 adapter.

## G2: deploying to a Camunda 8 cluster without multi-tenancy fails with the engine's error

**Status:** open, found 2026-08-10 while running the blueprints against a Camunda 8 cluster
in CI.

**What happens.** `name-clash-avoidance` defaults to `by-adapter`, which deploys the BPMN
resources into a tenant named after the workflow module. A self-managed cluster started
from the stock image has multi-tenancy switched off and rejects that, so the boot fails
with what Camunda said:

```
Failed to deploy BPMN resources of workflow module 'loan-approval' to Camunda 8 (adapter 'camunda8')!
Caused by: io.camunda.client.api.command.ProblemException: Failed with code 400: 'Bad Request'.
  detail: Expected to handle request Deploy Resources with tenant identifier 'loan-approval',
          but multi-tenancy is disabled
```

The remedy is `vanillabp.adapters.<id>.name-clash-avoidance: use-prefix` (or `none`), and
the adapter's own wiki states the rule: a cluster without multi-tenancy cannot use
`by-adapter`. The message does not, so the developer has to find the wiki page first, and
the engine's wording ("tenant identifier") does not name any VanillaBP property.

**What VanillaBP should do.** Recognise this rejection while deploying and re-raise it with
the property to change, naming the adapter id and the workflow module. Everything needed is
known at that moment. A check before deploying would be even better: whether the cluster has
multi-tenancy enabled can be asked, and a mismatch with the configured mode is a startup
error that names both sides.

**Affects:** the Camunda 8 adapter on every platform. Camunda 7 has no equivalent, its
name-clash avoidance is not tenant based.
