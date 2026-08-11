# Gaps found while building blueprints

Cases where writing a blueprint ran into something `spi-for-java` or
`adapter-platform-integration` does not cover, or covers in a way a developer only finds
out about by debugging. From here they move into the framework roadmap as stories.

One section per finding. Keep the reproduction concrete enough to be turned into a test.

## G1: an application-declared `@Transactional` silently disables the `TaskException` contract

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-11, found 2026-08-10 while building
`bpmn-service-task/springboot`. Kept here as the record of what the blueprints rely on.

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

**Reproduction, as it was.** In `bpmn-service-task/springboot`, add `@Transactional` to
`WorkflowTaskHandler` and run `LoanApprovalIT`:

```
LoanApprovalIT.aRejectedLoanTakesTheErrorPathAndKeepsWhatTheHandlerWrote
  The workflow '...' did not reach the expected state within PT30S.
  Last seen: Aggregate(loanRequestId=..., amount=500, creditRating=null, ratedBy=null, rejectionReason=null)
```

Without the annotation the same test passes, with `creditRating=5` and the rejection reason
persisted. The same annotation now fails the boot before any test runs.

**Why it will be hit.** Putting `@Transactional` on a service class is the default reflex in
a Spring Boot application, and it is the right thing to do for every method the API calls.
VanillaBP 1 even required `@Transactional(noRollbackFor = TaskException.class)` on the
service, so anybody migrating brings the annotation along. The blueprints work around it by
declaring transactions per method and writing down why, but a convention documented in a
blueprint is not a safeguard.

**How it was fixed.** The silence is gone, the annotation itself is still not allowed.
Neither Spring nor JTA lets a rollback-only mark be cleared again, and the application's
interceptor sits inside VanillaBP's transaction whatever propagation VanillaBP picks, so
there is nothing left to repair once the mark is set. VanillaBP makes the mistake impossible
to overlook instead, in two places:

1. At startup, in the `@WorkflowTask` scanner. A transaction annotation on the handler
   method, its class, a superclass, an interface or a custom annotation carrying one of them
   fails the boot with a message naming the method and the way out. It is a defect only if
   the propagation joins an existing transaction and the rollback rules do not exclude
   `TaskException`, so `@Transactional(noRollbackFor = TaskException.class)`, which version 1
   asked for, keeps booting.
2. At runtime, after the handler has run. If the transaction turns out to be rollback-only,
   the task fails with a message naming the workflow module, the BPMN process, the task and
   the handler method. This is what catches a transactional bean further down the call chain,
   which no startup check can see, and it also catches a handler swallowing the exception a
   nested bean threw.

**What that means for a blueprint.** Nothing to change in the code: `@Transactional` belongs
on the methods the API calls and nowhere near a task, which is what the blueprints already
do. The prose no longer has to carry the warning as a safeguard, since a reader who ignores
it gets a failed boot instead of a rejected loan looking untouched in the database.

**Affects:** every platform, since the mechanism is the platform's transaction interceptor
rather than the BPMS. On Quarkus, Spring's annotation only counts when `quarkus-spring-tx`
maps it onto the JTA one, and `jakarta.ejb.@TransactionAttribute` counts on Spring alone, so
each platform tells the check which annotations it honors.

## G2: deploying to a Camunda 8 cluster without multi-tenancy fails with the engine's error

**Status:** closed 2026-08-11, found 2026-08-10 while running the blueprints against a
Camunda 8 cluster in CI. Three changes, in the order they take effect:

1. `name-clash-avoidance` is an adapter-declared default now
   (`AdapterDeploymentService#defaultNameClashAvoidance`), and both Camunda adapters declare
   `none` - an application which configures nothing boots against a stock cluster. Since
   `none` keeps nothing apart, every adapter reports it per workflow module with a WARN
   naming its own alternatives (`warnAboutUnscopedIdentifiers`).
2. Configuration which only `by-adapter` could honor, and which no level of that adapter
   reaches, fails the boot instead of being ignored
   (`NameClashAvoidanceSupport#validateNoneNameClashStrategy`, called by both Camunda
   adapters with their `tenant-id` - the core knows nothing about tenants).
3. Where `by-adapter` does apply, the Camunda 8 adapter looks the tenant up in the cluster
   before deploying (`Camunda8TenantCheck`): multi-tenancy switched off and an unknown tenant
   both become a boot failure naming the property to change. Camunda 7 has nothing to ask -
   a tenant id is an attribute of the deployment there - so it only warns about a tenant
   missing in an identity service which knows others.

**What happens.** `name-clash-avoidance` defaulted to `by-adapter`, which deploys the BPMN
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

**What VanillaBP does now.** The cluster is asked before the deploy command is sent, which is
where the mismatch between the configured mode and the cluster's multi-tenancy becomes a
startup error naming both sides. An application which configures nothing never gets there,
because no tenant is used until it asks for one.

**Affects:** the Camunda 8 adapter on every platform. Camunda 7 uses tenants as well, but its
engine accepts any tenant name without one having to exist, so there is nothing that could
reject a deployment there.
