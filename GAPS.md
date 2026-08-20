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

**What that means for a blueprint.** The Camunda 8 snippet in both READMEs no longer sets
`name-clash-avoidance`, since nothing has to be configured to boot against a stock cluster.
The new WARN stays in the log on purpose: with one workflow module nothing can collide, and a
blueprint showing how VanillaBP asks for a decision is worth more than a quiet log. Both
READMEs explain it and name `accept-unscoped-identifiers` as the answer, so the blueprints
keep their configuration free of `vanillabp.*` properties.

**Affects:** the Camunda 8 adapter on every platform. Camunda 7 uses tenants as well, but its
engine accepts any tenant name without one having to exist, so there is nothing that could
reject a deployment there.

## G3: two branches of one workflow overwrite each other's writes on the workflow aggregate

**Status:** answered in VanillaBP 2.0.0-SNAPSHOT on 2026-08-15 (story 59), found 2026-08-14
while `bpmn-boundary-events/springboot` failed in CI on Camunda 8. Kept here as the record of
what an application has to decide.

**What happens.** A non-interrupting boundary event adds a token, so a side branch runs while
the task it is attached to stays open. Both branches work on the same workflow aggregate: the
side branch in a transaction VanillaBP opens for its task, the answer to the open task in a
transaction the application opens. Each loads the aggregate, changes what it is about, and
saves it.

JPA saves the whole row. Whichever transaction commits second writes back the values it read
when it started, so what the other branch committed in between is gone. Nothing reports it -
no exception, no warning, and the process itself behaves exactly as modelled.

**Reproduction.** `bpmn-boundary-events/springboot` against a Camunda 8 cluster, before the
test was changed: `LoanApprovalIT#aReminderLeavesTheTaskOpen` answered the task as soon as the
first of two reminders had been counted.

```
LoanApprovalIT.aReminderLeavesTheTaskOpen
  java.lang.AssertionError:
  Expecting actual not to be null
    at LoanApprovalIT.aReminderLeavesTheTaskOpen(LoanApprovalIT.java:98)
```

`partnerApproved` was null although the workflow had passed the task and informed the
customer: the second reminder loaded the aggregate before the answer was committed and saved
it afterwards. Locally the same test passed four runs out of four, with one run showing the
overlap in the log - reminder 2 at 09:22:15.853, the answer at 09:22:15.978. On the CI runner
it lost the race.

**Workarounds an application has today.** `@Version` on the aggregate turns the collision into
an optimistic locking exception, and `@DynamicUpdate` narrows each write to the attributes a
branch actually changed. Both are ordinary JPA and neither is mentioned anywhere in the
VanillaBP documentation, although the framework is what creates the second writer.

**Worth deciding for the framework.** Whether VanillaBP should recommend a version column for
workflow aggregates (documentation), detect concurrent branches touching one aggregate, or
say plainly that concurrent branches are the application's business. The blueprint states the
trade-off in its README for now, and its test answers the task after the last reminder so
that no side branch is in flight.

**Affects:** every BPMS, but only visibly a remote one. On an embedded engine the branches
rarely overlap long enough to lose a write, which is the worst kind of difference between
development and production.

**It happened again, 2026-08-20.** `bpmn-error-escalation` had the same situation and not the
annotation, in both twins: a non-interrupting escalation boundary event, so the branch behind it
and the task after the throw event run at the same time and both save the aggregate. The nightly
had been green until the Camunda 8 cluster of the CI moved from 8.8.34 to 8.9.16, which only
changed the timing enough to make it happen every run. What it looks like from the outside is
worth knowing: both handlers log their work, two milliseconds apart and on two threads, and the
test still times out waiting for both attributes, because one of the two writes is gone. Which one
survives differs per run - the CI lost the contract, the same build locally lost the supervisor.
`@DynamicUpdate` on the aggregate fixed it on both platforms. Every blueprint whose model creates
a second token was checked: `bpmn-boundary-events` had the annotation already,
`persistence-parallel-branches` and both multi-instance blueprints avoid the problem by keeping a
branch's result in an entity of its own, which is the better answer where it fits.

## G4: nothing says what happens when the aggregate cannot be saved because of a version conflict

**Status:** answered in VanillaBP 2.0.0-SNAPSHOT on 2026-08-15 (story 59), found 2026-08-14
while deciding how `bpmn-boundary-events` should survive two branches writing one aggregate.
Follows from G3, which is the same collision seen from the data side.

**What an application would do.** A `@Version` column is the standard answer to two writers,
and the only one that also covers two branches writing the SAME attribute, which
`@DynamicUpdate` does not. It turns the silent overwrite of G3 into an exception - and that
is where it stops being the application's business.

**Where it lands.** VanillaBP owns the transaction of a workflow task: it loads the
aggregate, calls the `@WorkflowTask` method, saves and commits. A version conflict therefore
surfaces in the commit VanillaBP performs, after the handler returned. The application
cannot catch it in its own code, and what becomes of it is whatever the BPMS does with a job
that failed - a retry on one engine, an incident on another, and no VanillaBP behaviour
between the two.

The other side of the same collision is the application's own transaction, opened around an
API call. There the application does control the retry, and nothing in the documentation
tells it that it needs one.

**Checked, not assumed.** `OptimisticLock`, `StaleObjectState` and `@Version` have no
occurrence at all in `adapter-platform-integration`, `spi-for-java`, `camunda7-adapter`,
`camunda8-adapter` and `process-engine-api-adapter`.

**To decide.** Whether a version conflict is a technical failure the BPMS retries, an
incident, or something VanillaBP retries itself; and if anything is retried, what that
demands of a handler, since a repeated handler sends the reminder a second time. That last
part is Story 51's question about inbound idempotency, so the two belong together.

**What the blueprints do meanwhile.** `bpmn-boundary-events` uses `@DynamicUpdate` and its
branches write different columns. `persistence-parallel-branches` will show one entity per
phase, which avoids the conflict rather than resolving it.

**What VanillaBP does now (story 59).** Two branches remain the application's design
decision - the framework neither locks the aggregate nor serializes the branches - but
nothing about it is silent any more:

- a BPMN process which can hold more than one token (a non-interrupting boundary event, a
  forking parallel or inclusive gateway, a parallel multi-instance activity, a
  non-interrupting event subprocess) is reported by the adapter while it wires the model, and
  the application logs one WARN per process if the workflow aggregate has no version
  attribute;
- a version conflict in the commit VanillaBP owns is named by one guiding ERROR and the
  exception is passed on unchanged, so the BPMS retries and ends in an incident. VanillaBP
  never retries by itself, because a handler may have called a remote API before the commit
  failed;
- the four ways an application can deal with it - an entity per phase, `@DynamicUpdate`, a
  version attribute plus a retry of its own, an additive relation - are described by the wiki
  page
  [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#two-writers-on-one-aggregate),
  which is where blueprints point instead of explaining it again;
- a delivery whose transaction failed leaves no record, so the retried delivery runs the
  handler again. That is the boundary of the inbound-idempotency feature, and the reason a
  handler with side effects has to survive repetition.

## G5: which process `startWorkflow` starts depends on the order classes are scanned in

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-15 (story `60`), found 2026-08-14 while
building `bpmn-call-activity-decomposition/springboot`.

**What happens.** Two classes annotated by `@WorkflowService` naming the same workflow
aggregate class - the pattern the SPI documentation shows for a call activity used for
decomposition:

```java
@WorkflowService(workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler { ... }

@WorkflowService(workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "risk_assessment"))
public class RiskAssessmentTaskHandler { ... }
```

`ProcessServiceBeanRegistrar` builds one `ProcessService` bean per aggregate class and takes
its BPMN process from `serviceClasses.getFirst()`, the first class the classpath scan
returned. `ClasspathScanner` does not sort, so which of the two that is comes out of the
file system. In the blueprint it was `RiskAssessmentTaskHandler`, and
`processService.startWorkflow(aggregate)` started the called process instead of the calling
one.

**Why it is bad.** Everything looks healthy. A workflow starts, a log line says
`started workflow 'risk_assessment'`, the tasks of that process run and write to the
aggregate. What is missing is the part of the business case the other process would have
done, and the only way to notice is to read the process ID in a log line nobody looks at, or
to have a test asserting an attribute the calling process writes.

**Reproduction.** The blueprint before its handler classes were merged. Rename the classes
and the outcome may change, which is the whole point.

**Context.** VanillaBP 1 had `@BpmnProcess.primary()` for exactly this. It is gone in
version 2 and nothing has taken its place, while `secondaryBpmnProcesses` still exists and
does the job as long as everything sits in one class.

**To decide.** Either the registrar picks deterministically and says how (a class declaring
a process which no call activity of the module calls, for instance), or a second workflow
service class on one aggregate class fails the boot with a message naming both classes.
Silently picking one is the option to drop.

**What the blueprint does meanwhile.** `bpmn-call-activity-decomposition` keeps the tasks of
both processes in one class and names the called process in `secondaryBpmnProcesses`. The
README and `AGENTS.md` say why, because the SPI documentation recommends the other way.

**How it was fixed.** `ProcessServiceBeanRegistrar` no longer takes whichever class the classpath
scan returned first: the classes declaring one aggregate are sorted, and where they name DIFFERENT
primary BPMN processes the boot ends with a message listing every class and its process, because
picking one would be a coin flip. A process reachable through a call activity is declared as a
`secondaryBpmnProcesses` entry of the class owning the primary process - which is what this blueprint
already does, so its workaround became the documented way.

## G6: Camunda 7 does not pass the business key to a called process, and nothing says so

**Status:** fixed 2026-08-17, found 2026-08-14 while building
`bpmn-call-activity-decomposition/springboot`. The Camunda 7 adapter merged
[PR #10](https://github.com/vanillabp/camunda7-adapter/pull/10) for framework story 61: the
propagation is injected while the BPMN is prepared, and the blueprint's models carry no
input mapping any more.

**What happens.** A call activity starts a new process instance. On Camunda 7 that instance
has no business key unless the model asks for one, and the business key is where the
Camunda 7 adapter keeps the ID of the workflow aggregate. The first task of the called
process therefore looks up an aggregate with no ID:

```
Error while evaluating expression: ${checkCollateral}. Cause: The given id must not be null
```

That is Spring Data speaking, through the engine, on a job which then retries and ends in an
incident. Neither the call activity nor the business key nor VanillaBP is mentioned.

**Reproduction, before the fix.** In `bpmn-call-activity-decomposition/springboot` with a
`camunda7-adapter` older than the fix, `LoanApprovalIT` fails as soon as the called process
reaches its first task.

**How it was answered.** The propagation is injected where listeners are attached and called
elements are rewritten anyway, and not blindly: only where the core says both processes work
on the same workflow aggregate, only for a static called element, only if the model passes no
business key already, and before name-clash avoidance rewrites the called elements. A called
process with an aggregate of its own would otherwise be handed the caller's identity.

**What that means for the blueprint.** The `camunda:in` element is gone from the model, and
with it the one place where the two engines needed a different line for the same thing. What
differs between them is what the adapter does, not what an application has to write.

**Affects:** Camunda 7. Camunda 8 works out of the box; the blueprint spells
`propagateAllParentVariables="true"` out anyway, because switching it off breaks the same
thing.

## G7: reuse of a called process, checked before building a blueprint for it

**Status:** closed on 2026-08-14, no gap. Recorded because the blueprint catalogue asks the
question.

**The question.** The catalogue lists `bpmn-call-activity-reuse` next to
`bpmn-call-activity-decomposition`, marked "to be checked": is a call activity whose process
is used by several unrelated parent processes something VanillaBP supports?

**The answer.** It is not a call activity in VanillaBP. A process used by different parents
is a business case of its own and therefore has a workflow aggregate of its own, and the
documented way to model that is a collapsed pool started from a service task rather than a
call activity - the same decoupling `module-interaction` shows between workflow modules.
`spi-for-java` states this in [Call-activities](https://github.com/vanillabp/spi-for-java#call-activities),
and it holds up in the code: the adapters find the aggregate of a called instance through the
identity of the parent (business key on Camunda 7, a propagated variable on Camunda 8), which
only makes sense while both instances share one aggregate.

**Consequence for the catalogue.** `bpmn-call-activity-reuse` has no blueprint of its own.
What it would show is a service task starting another workflow, which is
`module-interaction` inside one module, and the difference between the two situations is
explained where a reader meets it: in the README of
`bpmn-call-activity-decomposition`.

## G8: Camunda 8 supplies no multi-instance context, so the annotations do not work there

**Status:** fixed in the Camunda 8 adapter on 2026-08-15 (framework story 62), found the same
day while building `bpmn-multi-instance-task/springboot`. Kept as the record of what the
blueprint relies on and why that engine needed more than a getter.

**What happens.** A `@WorkflowTask` method may ask what the BPMS knows about the iteration it
runs in: `@MultiInstanceElement`, `@MultiInstanceIndex` and `@MultiInstanceTotal`. The Camunda
7 adapter answers them (`Camunda7WorkflowTaskBehavior.determineMultiInstances` walks the
execution hierarchy and reads `loopCounter` and `nrOfInstances`). The Camunda 8 adapter has no
implementation at all: `Camunda8TaskInvocationContext` does not override `getMultiInstances`,
so the SPI default `Map.of()` applies. Every iteration of a multi-instance task therefore
fails:

```
No multi-instance context named 'ServiceTask_RequestPartnerOffer' was supplied by the BPMS
adapter for the parameter 'partnerId' of @WorkflowTask method
'...WorkflowTaskHandler#requestPartnerOffer'! Supplied multi-instance contexts: [].
```

The job is failed, retried and ends in an incident. The message is a good one, and it is the
only reason this takes minutes rather than an afternoon: it names the parameter, the method,
the element asked for and what was supplied.

**Reproduction.** `bpmn-multi-instance-task/springboot` on the branch
`feature/bpmn-multi-instance-task`, run with `-Pcamunda8` against a cluster. The same
blueprint passes on Camunda 7 with the same Java code, which is the point: the model and the
code are fine, one adapter is not.

**What the cluster does deliver.** Zeebe creates one job per instance and puts the element
into a local variable named by `inputElement`, and the iteration index is available to the
model. So the values exist; nothing reads them into a `MultiInstanceValue` and hands them to
the core. What the adapter has to work out is the key of the map, which the SPI defines as the
ID of the multi-instance ELEMENT, and the nesting order (outermost first), which on this
engine has to come out of the model rather than out of an execution hierarchy.

**Beware of one difference.** Camunda 8 has no loop cardinality. A multi-instance element
there always iterates over an `inputCollection`, so the advice in the
[SPI documentation](https://github.com/vanillabp/spi-for-java#multi-instance) to prefer
`loop-cardinality` over a collection cannot be followed on this engine. The blueprint keeps
the collection to identifiers for that reason, and the documentation should say which engine
its advice applies to.

**Affects:** Camunda 8. It blocked two blueprints of the catalogue,
`bpmn-multi-instance-task` and `bpmn-multi-instance-subprocess`, because a blueprint is built
against every BPMS the build matrix knows.

**How it was fixed.** The adapter injects input mappings into every multi-instance element
while deploying, one set per element and named after it, carrying the index, the size of the
collection and the element. Those names cannot be shadowed, so a nested task sees the
iteration of the subprocess around it as well, and the total exists at all. The index is
translated to count from 0 like everywhere else. `bpmn-multi-instance-task` passes on both
engines with the same Java code; the adapter's README explains the mechanism under
"Multi-instance".

## G9: an API call into a running workflow can fail with the engine's optimistic locking

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-17 (story `63`), found 2026-08-15 when
`bpmn-boundary-events/springboot` failed once in CI on Camunda 7 and passed on the rerun.

**What happens.** The blueprint models a non-interrupting timer, so a reminder runs while the
task it is attached to stays open. Its test then answers that task through the application's
API, which is what an application does:

```java
service.partnerApproved(loanRequestId, taskId);
```

If a reminder job is being executed at that moment, two transactions touch the same
execution, and Camunda 7 refuses the later one:

```
LoanApprovalIT.aReminderLeavesTheTaskOpen:95 » OptimisticLocking ENGINE-03005 Execution of
'UPDATE ExecutionEntity[63]' failed. Entity was updated by another transaction concurrently.
```

The exception reaches the caller of `ProcessService#completeUserTask` - the application's own
API call - and nothing in VanillaBP's contract says what to do with it.

**Why this is not G3.** G3 and story 59 are about two writers of the workflow AGGREGATE, where
the loser silently overwrites. Here the engine notices and throws, and the loser is an
operation the application triggered. Camunda 7 retries its own jobs on this exception, which
is why the reminder survives and the API call does not.

**Reproduction.** Rare by nature: it needs the reminder job and the API call to overlap. It
appeared once in about twenty CI runs of this blueprint and passed on the rerun.

**Where a retry can live, checked in the code.** On Camunda 7 the core runs the operation in
PHASE ONE, inside the caller's transaction (`MigrationProcessService` only schedules an
outbox entry when the adapter needs a two-phase commit, which this one does not), and every
engine command joins that transaction through Camunda's `SpringTransactionInterceptor`
(a `TransactionTemplate` with propagation REQUIRED). A command which throws therefore leaves
the caller's transaction rollback-only.

That is also why the engine's own retry works and this one cannot be copied: the job executor
re-runs the WHOLE command in a transaction of its own
(`ExecuteJobHelper.callFailedJobListenerWithRetries`). The unit of work of an API call
belongs to the application and holds its aggregate changes, so repeating only the engine part
would either run in a transaction that can no longer commit, or - if it were given a
transaction of its own - let the process advance while the application rolls back.

**To decide.** Who repeats the unit of work: the application around its own call, or VanillaBP
around an operation an application handed it. What is clearly the ADAPTER's part either way is
the classification - only a Camunda 7 adapter knows that `OptimisticLockingException` means "a
concurrent transaction touched the same execution, repeating is safe". That is the same split
story 59 chose for the aggregate, where the platform classifies the conflict and the core
decides what happens. Story 51 belongs to the decision too: a repeated call must not send the
reminder twice.

**What it costs meanwhile.** A delivered blueprint has a test that fails roughly one run in
twenty. Either the blueprint retries the API call and says why, or the framework does - which
is the decision above.

**How it was fixed.** Since story `63` the Camunda 7 adapter answers a running workflow through the
same two-phase path as every remote BPMS: the engine command runs after the caller's transaction
committed, dispatched by the phase-two outbox. An `OptimisticLockingException` there is classified as
repeatable, so the next attempt of the outbox wins instead of the application seeing the collision -
the same thing Camunda's own job executor does with a failed job.

## G10: the Camunda 7 viewer failed for every application using the default configuration

**Status:** fixed in the Camunda 7 adapter on 2026-08-15, found the same day while building
`bpmn-history-and-diagram/springboot`.

**What happened.** The first call of `getProcessDefinitions` ended in

```
org.camunda.bpm.engine.exception.NullValueException: tenantIds contains null value
	at org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl.tenantIdIn(...)
	at io.vanillabp.camunda7.processservice.Camunda7WorkflowViewer.resolveHistoricInstance(...)
```

Three queries of the viewer passed the module's tenant id to `tenantIdIn()`. A workflow module
without a tenant is not an exotic setup: `name-clash-avoidance: none` is the DEFAULT of that
adapter, and then there is no tenant at all. Camunda does not read `null` as "no tenant", it
refuses the query - so the whole viewer API was unusable for a default-configured application,
in all three methods.

**Why nobody noticed.** The adapter's integration tests run with
`name-clash-avoidance: by-adapter`, where a tenant always exists. `Camunda7ProcessService` had
the guard (`tenantId != null ? tenantIdIn(tenantId) : withoutTenantId()`) from the beginning;
the viewer, written later, did not.

**How it was fixed.** The viewer uses the same guard, and a unit test asserts that a module
without a tenant asks without one. The lesson for the blueprints is the one they exist for: a
feature is not covered until something runs it the way a reader of the documentation would.

## G11: an ended workflow was unknown to Camunda 8 rather than completed

**Status:** fixed in the Camunda 8 adapter on 2026-08-15, found the same day while building
`bpmn-history-and-diagram/springboot`.

**What happened.** Asking for the history of a workflow which had ENDED raised

```
WorkflowNotFoundException: No configured BPMS knows the workflow of aggregate '...' - the
workflow history cannot be determined (probed adapters, in prioritized order: [camunda8])!
```

although the platform's wiki promises the opposite: "Viewing is not limited to running
workflows: as long as the BPMS still holds the workflow's data, definitions and history are
served for ended workflows too."

`awarenessOfWorkflow` searched the process instances with `state(ACTIVE)` and answered
`UNKNOWN_TO_BPMS` for everything else. That is the one answer with consequences: it permits
falling back to another adapter, the viewer turns it into the exception above, and an operation
arriving after the workflow ended fails instead of being ignored as too late.
`WorkflowAwareness.COMPLETED` exists for exactly this, and the core has branches for it which
this adapter could never reach.

**How it was fixed.** The search filters by the aggregate-ID variable alone and reads the state
of what it finds: an active instance means `ACTIVE`, anything else `COMPLETED`, nothing at all
`UNKNOWN_TO_BPMS`. The redispatch probe of the same class already did it that way, and said why
in its comment.

## G12: a shared derived getter is evaluated from a stale variable on Camunda 7

**Status:** fixed in the Camunda 7 adapter on 2026-08-18 (story `66`), found 2026-08-15 while
building `bpmn-aggregate-decoupling/springboot`.

**What the wiki asks for.** Page
[Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#fine-grained-control-over-attributes-synchronized-to-the-bpms)
recommends this shape for an aggregate decoupled from its BPMN, and calls it the way to
reduce synchronization to the bare minimum:

```java
@NoSyncWithBPMS
public class Aggregate {
  private RiskClass riskClass;
  @SyncWithBPMS public boolean isApprovableWithoutReview() { return riskClass == RiskClass.LOW; }
}
```

**What happens on Camunda 7.** The condition `${approvableWithoutReview}` is false for every
workflow, so every instance takes the default flow. No exception, no warning, a process that
runs to its end - and the wrong branch of a business decision.

**Why.** The adapter writes the shared values as process variables when the workflow starts
(`Camunda7ProcessService.operatorContext`, documented as the operator's view in Cockpit).
At that moment the risk class is not assessed yet, so the variable is written as `false`.
Camunda resolves a VARIABLE of that name before it asks VanillaBP's EL resolver, so the
gateway never sees the live aggregate again. The variables are refreshed at a few operations
(`refreshOperatorContext` for a task completion through the API, and for `aggregateChanged`),
but NOT when a `@WorkflowTask` method returns - which is where a derived value changes.

**Proven by narrowing, one run each.**

|          Aggregate          |             Condition             |    Result    |
|-----------------------------|-----------------------------------|--------------|
| getter without annotations  | `${approvableWithoutReview}`      | branch taken |
| getter with `@SyncWithBPMS` | `${approvableWithoutReview}`      | default flow |
| any                         | `${creditRating >= 30}` (a field) | branch taken |

The blueprint `bpmn-gateways` works for the same reason the first row does: it shares nothing,
so the resolver answers live.

**What makes it bad rather than surprising.** The application follows the documentation, the
BPMN reads a question rather than an attribute, and the answer is wrong. On Camunda 8 the same
model behaves correctly, because that adapter pushes the shared values when a task completes
(story 28b) - so the same code takes different branches on different engines, which is the
one thing VanillaBP promises never to do.

**To decide.** Either the Camunda 7 adapter refreshes the shared values at every sync point,
as the Camunda 8 adapter does, or it stops writing them under names an expression can read -
the operator context could carry a prefix, and expressions would always be answered live by
the resolver. The second is closer to how this engine works (it reads the aggregate directly
and needs no variables at all), the first keeps the two adapters symmetric.

**How it was fixed.** Camunda 7 shares the workflow aggregate like every other BPMS now: the shared
values are written at every point where the workflow can move on - the start, a task completion
including the BPMN-error path, complete and cancel of a task, a correlation - so a gateway behind a
task decides on what that task computed. Nested values travel as object variables in the
serialization format the application configures, which is why the adapter now also applies engine
plugins. The EL resolver over the aggregate stays in 2.0 as a migration fallback, with an existing
variable winning over it and a deprecation warning per name; story `79` removes it in 2.1.

**Proven by the blueprint it blocked.** `bpmn-aggregate-decoupling` was built on that fix on
2026-08-19 and its integration test passes on both engines: three amounts, three risk classes, the
branch each of them belongs to. The shape this gap was found with - a class annotated
`@NoSyncWithBPMS` whose two `@SyncWithBPMS` getters the conditions read - is what the blueprint
shows, so a regression fails a build instead of sending every workflow down the default flow again.

## G13: on Quarkus the phase-two dispatch reaches the application without a transaction

**Status:** fixed 2026-08-16, found 2026-08-15 while building `module-single/quarkus` against
Camunda 8. The platform integration merged
[PR #37](https://github.com/vanillabp/adapter-platform-integration/pull/37) for framework story
67: `PhaseTwoRouter` runs every dispatch through `TransactionRunner.requireTransaction`, which
joins an active transaction and starts one otherwise. On Quarkus that runner brings JTA plus
the request context, the same one the task path uses; on Spring Boot none is supplied on
purpose, because gruelbox brings the transaction. The `@Transactional` the Quarkus twins
carried on their repository is gone again.

**What happens.** A remote BPMS starts a workflow in two phases: the aggregate and an outbox
entry are written in the application's transaction, and the outbox dispatcher starts the
workflow in the BPMS afterwards. The dispatcher runs on a thread of its own, and there it
calls back into the application: the adapter needs the aggregate to build the variables it
sends to the engine, so it asks the application's `AggregatePersistenceAware`.

On Quarkus that call arrives with neither a transaction nor a CDI request context active,
and an entity cannot be read that way. The workflow start fails and is retried forever:

```
WARN  [JdbcPhaseTwoOutboxDispatcher] Dispatching phase two (START_WORKFLOW) of BPMN process
 'loan_approval' of workflow module 'loan-approval' for aggregate '356d…' failed - will retry:
 jakarta.enterprise.context.ContextNotActiveException: Cannot use the EntityManager/Session
 because neither a transaction nor a CDI request context is active. ...
   at io.vanillabp.camunda8.processservice.Camunda8ProcessService.variablesOf
   at io.vanillabp.integration.runtime.outbox.JdbcPhaseTwoOutboxDispatcher.dispatch
```

The application looks healthy: the aggregate is in the database, the API answered, and the
workflow simply never appears in the BPMS. The message names Quarkus' remedy ("add
`@Transactional` to your method"), which points at the application although the call comes
from VanillaBP's own thread.

**Reproduction.** `blueprints-modules/module-single/quarkus`, remove `@Transactional` from
`AggregateRepository`, start a Camunda 8 cluster and run
`mvn -Pcamunda8 install verify`. `LoanApprovalIT` times out with the aggregate stored and
`creditRating=null`.

**Why it was not seen before.** Every Quarkus test of the platform and of the adapters
implements `AggregatePersistenceAware` with a map, and a map needs no transaction. The
blueprint is the first Quarkus application storing its aggregate the way applications do.

**Where it belongs.** The `@WorkflowTask` path already runs in a context VanillaBP opens
(`QuarkusTransactionRunner`), and the phase-two path should do the same rather than expect
every application to annotate its persistence. Spring Boot does not show the problem because
its repositories open an `EntityManager` per call, so this is a platform difference the
application would have to know about, which is exactly what a platform integration is there
to remove.

**It hits VanillaBP's own persistence too, since story 69.** The default implementations for
the persistence patterns of this platform (`PanacheRepositoryAggregatePersistence`,
`PanacheActiveRecordAggregatePersistence`, ...) reach the database from wherever VanillaBP
calls them, and they open no transaction on purpose. So the gap is no longer "the
application has to annotate its persistence" but "VanillaBP cannot use its own default on
the phase-two path".

Proven on 2026-08-15 with the blueprint: as a Panache **active record** (no repository, no
class of the application in the path at all) `LoanApprovalIT` fails on Camunda 8 with the
exception above, and there is nowhere left to put the annotation.

**What the blueprint did until then.** The aggregate is stored by a Panache **repository**,
and that repository carried `@Transactional` for a day. VanillaBP's default called the
repository, the interceptor applied, and the transaction was there.

**How it was answered.** The annotation is gone from all seventeen Quarkus twins since
2026-08-16: the repository is a plain `@ApplicationScoped` bean again, and the class comment
no longer has to explain a transaction the application does not own. With the guarantee in
the platform, a blueprint showing the active record is possible as well - that is the shorter
code and the reason the pattern exists.

## G14: a workflow module tested on Quarkus does not find its own BPMN files

**Status:** fixed 2026-08-16, found 2026-08-15 while building `module-single/quarkus`. The
platform integration merged
[PR #40](https://github.com/vanillabp/adapter-platform-integration/pull/40) for framework
story 68: where the application IS the workflow module, the convention names both locations,
the module's own one first, and the first location holding files wins. The property every
Quarkus twin carried in its test configuration, and the resource filtering feeding it, are
gone again.

**What happens.** Where BPMN files are read from follows a convention: a workflow module
shipped as its own artifact keeps them below its ID (`loan-approval/processes/<adapter-id>`),
whereas an application which IS the workflow module keeps them below `processes/<adapter-id>`.
Which of the two applies is decided by whether the module descriptor comes from the
application's main artifact.

A workflow module tested inside its own Maven module is the main artifact on Quarkus, so the
convention drops the module ID - while the files sit where the packaged application needs
them, below the ID. Booting the test therefore reports:

```
WARN No executable BPMN processes found for workflow module 'loan-approval' at location
 'classpath*:processes/camunda7'! Adapter 'camunda7' is skipped for this workflow module.
```

and starting a workflow fails afterwards with the engine's own error (`no processes deployed
with key 'loan_approval'`), which names neither the location nor VanillaBP.

The same module in the application deploys correctly, so the failure appears only in the test
which is supposed to prove the module works.

**Reproduction.** `blueprints-modules/module-single/quarkus`, remove the `vanillabp` section
from `loan-approval/src/test/resources/application.yaml` and run `mvn clean verify`.

**Why the other platform does not show it.** There the module's test application lives in the
test sources, whose classpath root is not the one carrying the descriptor, so the module
counts as its own artifact and the convention matches the files.

**What the blueprint did until then.** The module's test named the location per adapter
(`loan-approval/src/test/resources/application.yaml`, naming the BPMS of the active Maven
profile through resource filtering). It was the only property the blueprints configured.

**How it was answered.** The first way: a module's resources are where the module puts them,
whoever runs it. Where the application is the workflow module, both locations are looked at,
the module's own one first, and only the first one holding files is deployed - so a process
is never deployed twice. Two messages came with it: the warning about an empty location names
both places now, and a workflow module no adapter found any resources for is an error rather
than a quiet start with nothing to run.

## G15: a bean only VanillaBP looks up is dropped while a Quarkus application is built

**Status:** fixed 2026-08-16, the same day it was found while building
`bpmn-multi-instance-subprocess/quarkus`. The platform integration merged
[PR #38](https://github.com/vanillabp/adapter-platform-integration/pull/38): the build step
collects the classes VanillaBP resolves by name from the index and keeps them, so an
application needs no Quarkus annotation and stays the same on both platforms.

**What happens.** A multi-instance resolver is a bean of the application which nothing
injects: it is named in an annotation,
`@MultiInstanceElement(resolverBean = IterationResolver.class)`, and VanillaBP asks the bean
container for it when the task runs. Quarkus removes beans nobody injects while it builds the
application, so the lookup finds nothing and every iteration of the task fails:

```
Error while evaluating expression: ${requestPartnerOffer}. Cause: No bean of the resolver
class 'blueprint.workflowmodule.loanapproval.IterationResolver' (used by the parameter
'iteration' of @WorkflowTask method
'blueprint.workflowmodule.loanapproval.WorkflowTaskHandler#requestPartnerOffer') is
available! Define it as a bean of your application.
```

The message is VanillaBP's own and it is precise about what is missing, but it points at the
application while the class is annotated correctly. What is missing is the instruction to
Quarkus to keep the bean.

**Reproduction, before the fix.** `blueprints-bpmn/bpmn-multi-instance-subprocess/quarkus`
with a `vanillabp-quarkus-integration` older than the fix: all three tests time out.

**Why the other platform does not show it.** Spring Boot keeps every bean it finds, whether
anything injects it or not.

**What the blueprint did until then.** The resolver carried `io.quarkus.arc.Unremovable` next
to `@ApplicationScoped` for a few hours. It is gone again, which is the point: the annotation
was the one line of Quarkus in code that is supposed to read the same on both platforms.

**How it was answered.** The build step which reads `@MultiInstanceElement` marks the resolver
class itself, and the repository of the default aggregate persistence is marked the same way.
The verdict about a class which is no bean at all moved to the build, and the runtime message
now names what is left as a cause there: a class the build never saw, e.g. a workflow module
shipped without an index.

## G16: Camunda 7 on Quarkus never reports the end of a workflow

**Status:** fixed 2026-08-16, the same day it was found while building
`bpmn-workflow-ended/quarkus`. The Camunda 7 adapter merged
[PR #9](https://github.com/vanillabp/camunda7-adapter/pull/9): both platforms build the
engine holder through one constructor which takes every invoker the core offers, and the
deployment service now compares what the application registered with what the engine can
deliver and says so while deploying. The Quarkus twin of the blueprint is part of the
catalogue since then.

**What happens.** A `@WorkflowEnded` method is registered like every other handler and the
application boots without a word of complaint, but the method is never called. The workflow
reaches its end event and the attributes the notification was supposed to fill stay empty.
Nothing is logged, because nothing failed.

The Camunda 7 adapter attaches its end listener while it parses the BPMN, and only when it has
a `WorkflowEndedInvoker` to call:

```java
if (workflowEndedInvoker != null) {
  parseListener.setWorkflowEnded(...);
}
```

On Quarkus the invoker is never passed. `Camunda7EngineProducer` builds the engine holder with
the seven-argument constructor, which fills the invoker with `null`, although the very
registry it already passes twice implements the interface.

**Reproduction, before the fix.** `blueprints-bpmn/bpmn-workflow-ended/quarkus` on Camunda 7:
its two tests waited for `closedAt` and timed out.

**Why the other platform does not show it.** The Spring Boot registrar looks the invoker up and
passes it. The Camunda 8 Quarkus adapter passes it as well
(`Camunda8DeploymentServiceProducer#setWorkflowEndedInvoker`), so this is one adapter on one
platform rather than a hole in the SPI.

**What the blueprint did until then.** Nothing can be done in an application, so the Quarkus
twin of `bpmn-workflow-ended` was held back for a day.

**How it was answered.** Passing the registry a third time was the fix, and two things keep the
same hole from opening again. The engine holders of both platforms have one constructor now,
which takes every invoker the core offers, so a forgotten argument is a compile error instead
of a feature switched off in silence. And the deployment service compares what the application
registered with what the engine can deliver: a `@WorkflowEnded` method no adapter will ever
call is reported while the BPMN is deployed rather than noticed when a workflow ends in
production.

## G17: on Camunda 8 one adapter runs everything on a single thread, and nothing says so

**Status:** open, found 2026-08-17 while building `persistence-parallel-branches`.

**What happens.** A `@WorkflowTask` handler which takes its time holds a thread of the BPMS.
On Camunda 8 that thread comes from the client's job worker executor, whose default size is
one (`DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS = 1` in `CamundaClientBuilderImpl`), and every
worker of an adapter shares it: the tasks of all workflow modules, the user task listeners,
the events a BPMS-initiated start reports, the notification that a workflow ended.

One handler waiting is therefore enough to stop everything that adapter would deliver. The
application looks alive, the cluster looks alive, and nothing arrives.

The blueprint ran into it as a test: one branch was held inside its task handler while the
test answered the other branch, which waits at a user task. The user task never appeared,
because the event announcing it needed the very thread the held handler was sitting on. The
same test passed locally often enough to look fine, and failed in CI, which is what a race
looks like from the outside.

**Reproduction.** Any `@WorkflowTask` method with a `Thread.sleep` of a few seconds, plus a
second thing to be delivered while it sleeps: a second task of another workflow, a user task
of the same workflow, the end of a workflow. Nothing is delivered until the sleep is over.

**Why the other engine does not show it.** Camunda 7 is embedded and its job executor has
three threads by default, and the jobs of one process instance are exclusive anyway, so the
same handler blocks that instance rather than the adapter.

**What the blueprint does about it.** It does not block a handler at all. Both branches of
`persistence-parallel-branches` wait for the application first, the document service is made
slow rather than held, and the assertion is the timestamps of the two records. The README
says why, because a reader copying the test into their own project would otherwise write the
version which stalls their adapter.

**To decide.** Framework story 74 (`prompts/74-camunda8-worker-threads.md`) carries it. Two
questions, and the second is the one that matters:

1. Should the number of execution threads be configurable, e.g.
   `vanillabp.adapters.<id>.worker-threads`? The Camunda client takes it
   (`numJobWorkerExecutionThreads`), the adapter passes nothing today.
2. What does VanillaBP promise about the thread a handler runs on? An application must know
   whether blocking in a handler is a local decision or a global one, and the answer differs
   per BPMS. It belongs on the BPMS adapter pages of the wiki, next to what a task delivery
   guarantees, rather than in a blueprint.

**Measured again on 2026-08-20, and it is not only a blueprint's test.** Two blueprints failed on
Camunda 8 in BOTH runs of the same commit, which rules out an unlucky moment:
`persistence-parallel-branches` in both twins (`bothBranchesKeepTheirResult`, thirty seconds waiting
for the second branch) and `persistence-flyway/quarkus` (`theProcessRunsThrough`, TWO MINUTES waiting
for a single service task, which is the harness timeout of that test). Both wait for a delivery which
does not arrive; neither blocks a handler.

Load is not the explanation, which the next run settled: `persistence-parallel-branches/quarkus`
failed the same way in a run of ONE matrix, where every job has a runner and a cluster of its own.
What the aggregate looked like there says which half arrived:

```
Last seen: Aggregate(..., creditRating=50,
  partnerApproval=PartnerApproval(id=2, taskId=2251799813685395, approvedBy=null, approvedAt=null),
  documentCheck=null, customerInformed=null)
```

One branch of the parallel split was delivered, the other was not, within thirty seconds. The
Camunda 8 client hands both to one execution thread, so two branches of one workflow are two
deliveries competing for it. Story `74` decides whether that thread count becomes configurable and
what VanillaBP promises about the thread a handler runs on. Until then a test on this engine which
waits thirty seconds for the second of two parallel branches is a test which goes red often enough
to be noticed, and a busy machine makes it worse rather than causing it.

## G18: the startup line about a transaction names the bean's proxy on one platform

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-18 (story `80`,
[PR #48](https://github.com/vanillabp/adapter-platform-integration/pull/48)), found 2026-08-17
while building `persistence-custom`.

Story 70 writes one line per workflow aggregate saying which transaction VanillaBP processes it
in, and it is the fastest check that an application-provided `TransactionRunner` is really being
used. On Spring Boot it names the bean:

```
... is processed in the transaction of: the TransactionRunner bean 'unitOfWork' of the application
```

On Quarkus it names the proxy the bean container puts in front of that class:

```
... is processed in the transaction of: the TransactionRunner bean
'blueprint.workflowmodule.loanapproval.persistence.UnitOfWork_ClientProxy' of the application
```

**Why it matters more than it looks.** The line exists so that a developer can see whose
transaction is used, and both blueprints of this wave point at it. A name ending in
`_ClientProxy` sends the reader looking for a class which is not in their sources. The
README of `persistence-custom` explains the suffix, which is a workaround for a message.

**How it was fixed.** `QuarkusTransactionRunnerResolver` reads its beans through
`Instance#handles()` and names `Handle#getBean().getBeanClass()`, the class the application wrote.
No suffix is stripped from a runtime class name, and where no bean metadata is available the
runtime class is still named. The two ambiguity messages of the same resolver carried the defect
as well and follow the rule now. The Spring Boot side needed nothing, it names the bean name.

The README of `persistence-custom/quarkus` lost the sentence about the suffix and quotes the real
line again.

**Affects:** Quarkus, `QuarkusTransactionRunnerResolver`.

## G19: an application without any BPMS adapter fails with the bean container's words, not VanillaBP's

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-18 (story `81`,
[PR #48](https://github.com/vanillabp/adapter-platform-integration/pull/48)), found the same day
while moving the BPMS-specific configuration of the blueprints into profiles.

**What was tried.** Every blueprint chooses its BPMS with a Maven profile, so leaving the profile
out is the fastest way to see what an application does with no adapter on the classpath - the
mistake somebody makes on their first day.

**Spring Boot.** The application does not boot, and the message is Spring's:

```
Error creating bean with name 'apiController': Unsatisfied dependency expressed through field
'service': ... Error creating bean with name 'workflow': Unsatisfied dependency expressed through
field 'processService': No qualifying bean of type
'io.vanillabp.spi.process.ProcessService<blueprint.workflowmodule.loanapproval.model.Aggregate>'
available: expected at least 1 bean which qualifies as autowire candidate
```

Nothing in it says the word adapter, names a dependency to add, or mentions VanillaBP at all. A
developer reads it as a mistake in their own wiring, because that is what it looks like.

**Quarkus.** The same situation is reported by VanillaBP:

```
java.lang.IllegalStateException: No extensions found with capabilities 'io.vanillabp.adapter.*'!
Add Quarkus extensions providing VanillaBP adapters.
```

Right in kind, and one step short of what the configuration-validation rule asks for: it does not
name the extensions a developer could add.

**Why it matters.** This is the first message of the first attempt. The platform integration
validates plenty at startup, so a reader of the other messages expects the same here, and the
blueprints teach exactly that: read the startup log, it names the remedy.

**How it was fixed.** Both platforms now answer with VanillaBP's own message, and it points at
the wiki page of the available adapters instead of naming them in compiled code - adapters are
released independently, so a list inside a released JAR would age.

On Spring Boot the check could not live in the integration: an adapter is what brings
`vanillabp-spring-boot-integration` along, so an application without an adapter has no VanillaBP
runtime at all. It sits in `vanillabp-spring-boot-support`, the module every workflow module
compiles against, as an autoconfiguration registering a `BeanFactoryPostProcessor` - before the
first bean, hence before any `ProcessService` injection point. It reports only when a
`META-INF/workflow-module` marker is present, so an application without a workflow module still
boots silently. The case "integration loaded, adapter missing" stays in the integration, which
knows the adapters it found.

```
No VanillaBP BPMS adapter found in classpath! A workflow module was found (a 'META-INF/workflow-module'
marker file), but no adapter which could run its workflows - and on Spring Boot an adapter is also what
brings VanillaBP's Spring Boot integration, which is missing as well.

Add a BPMS adapter as a dependency of your application. Which adapters exist, and the Maven coordinates
of each one, is listed at
  https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters
...
```

On Quarkus the wording of the existing message was aligned and points at the same page for the
extension names. One case is left and cannot be closed (story `82`): an application without the
core extension `vanillabp-quarkus-integration` has no VanillaBP code in its build at all, so
Quarkus reports an unsatisfied dependency for `ProcessService` itself. A build step of ours would
have to live in a workflow module's dependency, and a workflow module stays free of platform
infrastructure. Both wiki pages name that error instead.

**Affects:** the Spring Boot integration primarily, the Quarkus one for the wording.

## G20: how a workflow module is published for Spring Boot is nowhere written down

**Status:** closed 2026-08-18 by story `83`. The wiki page `Workflow-modules-in-Spring-Boot`
has the section *Publishing a workflow module: it brings its own wiring*, written from what
building `module-multi` actually ran into: the auto-configuration recipe, the symptoms without
it, the four kinds of names which collide once two modules meet in one application, and the
fallback. The other platform page and the platform-independent page point at it. Found on
2026-08-18 while writing the prompt for `module-multi`.

**What is missing.** On Spring Boot a workflow module which somebody else consumes has to bring
its own wiring: an `@AutoConfiguration` class registered through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, which
contributes the module's beans, its entities and repositories, its properties class and whatever
else only the module can know. That is how it is done in practice, and it is the reason an
application can compose a runtime out of modules it only gets as dependencies.

The wiki does not mention it. `Workflow-modules-in-Spring-Boot` explains the marker file, the
namespaces and the configuration files, and stops where the interesting part begins: the
application's component scan starts at the application's package, so a module in a package of its
own is invisible to it unless the module says otherwise.

**Why it matters here.** A blueprint links the reference documentation instead of repeating it.
For `module-multi` and `module-packaging` there is nothing to link, so either the READMEs grow an
explanation which belongs in the wiki, or the blueprints teach less than they should. The
counterpart on Quarkus is documented (the Jandex index, and the marker file being enough for a
module of resources), which makes the gap look like an oversight rather than a decision.

**What would fix it.** A section in `Workflow-modules-in-Spring-Boot` naming the recipe: what the
auto-configuration contributes, where it is registered, what happens without it (the symptoms:
beans not found, entities not mapped, repositories missing), and the fallback for a module which
does not bring one (`@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories` in the application,
with the note that the application then knows things about a module it should not).

**Affects:** the wiki of `adapter-platform-integration`, Spring Boot side.

## G21: a Quarkus application with two persistences has to attribute the outbox itself

**Status:** fixed in VanillaBP 2.0.0-SNAPSHOT on 2026-08-18 (story `84`,
[PR #49](https://github.com/vanillabp/adapter-platform-integration/pull/49)), found the same day
while adding the MongoDB use case to `persistence-active-record`. The blueprint is the first application with two persistences in one
workflow module: the loan approval is a Hibernate ORM Panache active record, the credit history a
MongoDB Panache active record.

**What happens.** The application does not start. Both default outboxes are registered, because
both a datasource and a MongoDB client are configured, and the resolver cannot attribute either of
them to an aggregate:

```
java.lang.IllegalStateException: Several PhaseTwoOutbox beans exist ([...JdbcPhaseTwoOutbox_ClientProxy,
...MongoPhaseTwoOutbox_ClientProxy]), but none can be attributed to workflow aggregate
'blueprint.workflowmodule.credithistory.model.Aggregate'! Outbox entries must be enlisted in the
transaction persisting the aggregate. To solve this either
- provide a bean implementing io.vanillabp.integration.spi.PhaseTwoOutboxAware for this aggregate
  (returning the outbox matching its persistence), or
- deactivate the unwanted default outbox ('vanillabp.outbox.jdbc.enabled' / 'vanillabp.outbox.mongo.enabled').
```

It fires at startup, from `MigrationProcessService#validatePhaseTwoOutboxAtStartup`, on the
embedded engine as well: the Camunda 7 adapter starts workflows in two phases like every other
adapter since story 63. The log of delivered tasks has the same ambiguity, one step later.

Neither remedy fits. Deactivating a default takes the store away from the other aggregate, which
then has no outbox at all. And an attribution bean has to inject `MongoPhaseTwoOutbox` or
`JdbcPhaseTwoOutbox`, which live in `vanillabp-quarkus-integration` - an artifact a workflow module
must not depend on, so the beans would have to exist twice, in the application and in the test
sources of the module.

**Why it is a gap rather than a decision.** VanillaBP itself picked the persistence of both
aggregates while the application was built, and it says so:

```
Using VanillaBP's MongoDB Panache active record persistence for workflow aggregate '...credithistory.model.Aggregate'
Using VanillaBP's Hibernate ORM Panache active record persistence for workflow aggregate '...loanapproval.model.Aggregate'
```

The Javadoc of `QuarkusPhaseTwoOutboxResolver` explains the ambiguity with "Quarkus has no
platform-side knowledge of which persistence manages an aggregate". Since story 69 that is only
true for an aggregate the application wrote an `AggregatePersistenceAware` for. Where VanillaBP
chose the idiom, it also knows which store the aggregate's transaction reaches.

**How it was fixed.** `QuarkusPersistenceTechnology` reads the technology off the persistence
VanillaBP resolved for the aggregate, and both Quarkus resolvers now attribute like their Spring
Boot counterparts have since story 70: Hibernate ORM Panache and Spring Data lead to the JDBC
outbox and delivery log, MongoDB Panache to the MongoDB ones. A `PhaseTwoOutboxAware` or
`TaskDeliveryLogAware` bean still wins, an aggregate whose persistence the application brought
itself keeps the old message - there nobody but the application knows the store - and a single
default of the other technology ends the boot rather than writing entries next to the aggregate
instead of into its transaction.

The blueprint needs no attribution bean, which is what its README now explains, and the MongoDB use
case of `persistence-active-record` builds and runs.

**Affects:** `adapter-platform-integration`, Quarkus side.

## G22: a Quarkus application without MongoDB does not build natively

**Status:** open, story `85` (2026-08-19), found while building the delivery part of the
blueprint `module-packaging`. The blueprint's application has a relational database and no
MongoDB anywhere.

**What happens.** `mvn install -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true`
ends in the image builder:

```
Error: Discovered unresolved method during parsing:
io.vanillabp.integration.runtime.processservice.QuarkusMongoDeployment.isReplicaSet().
This error is reported at image build time because class
io.vanillabp.integration.runtime.processservice.QuarkusTransactionRunnerResolver is
registered for linking at image build time by command line and command line.
Caused by: java.lang.NoClassDefFoundError: org/bson/conversions/Bson
```

`QuarkusTransactionRunnerResolver#coverageOf` calls `QuarkusMongoDeployment.isReplicaSet()`,
and that class imports `com.mongodb.client.MongoClient` and `org.bson.Document`. On the JVM
that is harmless: the method is only reached for an aggregate MongoDB manages, and a class is
loaded when it is first used. A native image resolves every referenced method while it is
built, so the missing MongoDB driver ends the build of an application which never wanted one.

**Why it is a gap rather than a decision.** The same code base already knows the rule and
follows it elsewhere: MongoDB Panache is optional, so the persistence implementations are
recognized BY NAME (`QuarkusPersistenceTechnology`, `AggregateWrite#causedByOptimisticLocking`).
The replica-set probe is the one place where a MongoDB type is referenced directly, and the
build of a native image is where that shows.

**What would fix it.** Reach the probe the way the rest of the class reaches optional types:
by name and by reflection, or behind a bean which only exists when the MongoDB client
extension is present, so that nothing links `org.bson` into an application without MongoDB.
Whatever the shape, the acceptance is a native build of an application with a relational
database only.

**Affects:** `adapter-platform-integration`, Quarkus side. Blocks the native build of every
Quarkus application without MongoDB, and with it the delivery section of `module-packaging`.

## G23: handing VanillaBP's tables over forces an application to write gruelbox's DDL as well

**Status:** closed on 2026-08-20 by story `95` (`adapter-platform-integration` PR #57) and the two
blueprints which found it, `persistence-liquibase/springboot` and `persistence-flyway/springboot`,
the first ones to take the schema out of the runtime's hands. Read from the code, not guessed:
`GruelboxPhaseTwoOutboxAutoConfiguration` calls
`DefaultPersistor.builder()...migrate(properties.isCreateSchema() && (customTable == null))`.

**What happens.** `vanillabp.outbox.create-schema` is one switch for two things. An application
which wants Liquibase or Flyway to own `VANILLABP_PHASE_TWO_OUTBOX` and
`VANILLABP_TASK_DELIVERY` has to set it to `false`, and that switches off the migrator of
gruelbox, which owns `TXNO_OUTBOX` on Spring Boot. Story `75` decided, for good reasons, that
VanillaBP ships no statements for a schema belonging to a third party. So the application is left
to write them.

**The DDL is not a procedure any more.** gruelbox writes its own statements:
`DefaultPersistor.builder().dialect(<dialect>).build().writeSchema(writer)` emits every migration
of the library as SQL for that dialect. Verified against `transactionoutbox-core` 7.0.707: eleven
statements for H2 out of thirteen migrations, two of which do nothing on this dialect. They create
`TXNO_OUTBOX` and `TXNO_SEQUENCE` and not `TXNO_VERSION`, which is the bookkeeping of the migrator
just switched off. `persistence-liquibase/springboot` and `persistence-flyway/springboot` carry
exactly that output, and their `GruelboxSchemaDriftTest` asks for it again on every build and
compares the statements, so an upgrade of the library fails a build instead of a deployment. No
database is started for the comparison. Before that the statements had been read out of a migrated
H2 database, which worked and was a procedure no application should have to invent.

**The decision.** Option 2 below: gruelbox stays, its DDL stays the application's job, and the
silence goes. Option 3 was rejected again, for the reason story `75` rejected it, and option 1
remains the open question of story `26i`.

**The silence is gone too.** `TXNO_OUTBOX` used to be the one table nobody verified, so an
application which switched the creation off and forgot it booted cleanly, served requests, and
failed at the first workflow it started, which is exactly the 3 a.m. failure story `75` set out to
remove. The Spring Boot integration now checks that table wherever gruelbox's migration is off and
ends the boot naming the table, the property, `writeSchema` and the wiki section. Both blueprints
cover it: `MissingTableIT` starts the application once without VanillaBP's part of the schema and
once without gruelbox's, and each boot ends with the message about the table that is missing. On
Quarkus the question never arose: there the outbox is VanillaBP's own and its table is both shipped
and checked.

**Left over, and not this gap.** Whether Spring Boot should get VanillaBP's own JDBC outbox, the one
Quarkus uses, so that no application writes a foreign schema at all. That is option 1 below and the
question story `26i` carries.

**The three options, for the record.**

1. Give the Spring Boot integration VanillaBP's own JDBC outbox, the one Quarkus already uses.
   Then every table VanillaBP needs is described in `vanillabp-schema`, checked at startup, and
   the two platforms are symmetric. This is the open question story `75` names in its decision 3
   and `26i` carries.
2. Keep gruelbox and check its table at startup like the others, with a message naming the
   table, the property and where the library documents its schema. **Chosen.**
3. Ship gruelbox's DDL after all, generated from its own migrator by a build of the framework
   rather than copied by hand. That pins a foreign schema in VanillaBP's artifacts, which is
   what decision 3 of story `75` rejected.

**Affects:** `adapter-platform-integration`, Spring Boot side. Anything which manages its
database schema itself, which is every application past its first prototype.

## G24: the wiki recommends a second Flyway instance without saying what it needs

**Status:** open, story `96` (`prompts/ROADMAP.md`), found on 2026-08-19 while building
`persistence-flyway`. A documentation gap, not a defect in the code, and it costs whoever follows the
wiki an hour of confusion.

**What the wiki says.** Story `75` decided, correctly, that VanillaBP's SQL is applied by a Flyway
instance of its own, with a history table of its own, so that VanillaBP's version numbers never
collide with an application's. The wiki pages of both platforms say so and name
`flyway_schema_history_vanillabp`.

**What happens when you do that.** The second instance refuses to work:

```
org.flywaydb.core.api.FlywayException: Found non-empty schema(s) "PUBLIC" but no schema
history table. Use baseline() or set baselineOnMigrate to true to initialize the schema
history table.
```

Any instance which is not the first one finds a schema that already holds tables and no history of
its own, which is exactly the situation Flyway treats as "somebody else's database". It needs
`baselineOnMigrate = true`. And with that comes a second setting nobody thinks of:
`baselineVersion` defaults to 1, so every migration numbered `1.0.0` is marked as already applied
and silently skipped. Without `baselineVersion = 0` the tables of such a migration are simply never
created, and Flyway reports success.

**What would fix it.** Two sentences in the Flyway section of both platform pages: a history table
of its own needs `baselineOnMigrate`, and `baselineVersion` has to be `0` unless every migration is
numbered above 1. The blueprint shows both settings and says why, but the wiki is where somebody
looks first.

**And on Quarkus the recommendation does not hold at all.** A history table of its own needs a Flyway
instance of its own, and there is no way to have one without paying for it:

- the extension applies ONE configuration per datasource, and a named configuration without a
  datasource of the same name is ignored, even with a `jdbc-url` of its own (measured: the
  configuration is silently skipped, and the first table Hibernate looks for is missing),
- a migration run of the application's own comes too late, because Hibernate builds its session
  factory - and with `schema-management.strategy: validate` compares it against the schema - before
  any `StartupEvent` observer runs (measured the same way).

So a history per owner costs either a datasource per history, whose pool exists for one migration, or
the startup validation. `persistence-flyway/quarkus` therefore applies every owner's migrations in
one timeline and keeps the version ranges apart, which the blueprint explains. What the wiki should
say is what the platform allows, per platform, rather than one recommendation for both.

**Affects:** the wiki of `adapter-platform-integration`, both platform pages. Anything which follows
the recommendation of story `75` and manages its schema with Flyway.
