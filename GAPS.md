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

**Status:** open, found 2026-08-14 while `bpmn-boundary-events/springboot` failed in CI on
Camunda 8.

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

## G4: nothing says what happens when the aggregate cannot be saved because of a version conflict

**Status:** open, found 2026-08-14 while deciding how `bpmn-boundary-events` should survive
two branches writing one aggregate. Follows from G3, which is the same collision seen from
the data side. Framework story 59 in `prompts/ROADMAP.md`.

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

## G5: which process `startWorkflow` starts depends on the order classes are scanned in

**Status:** open, found 2026-08-14 while building `bpmn-call-activity-decomposition/springboot`.

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

## G6: Camunda 7 does not pass the business key to a called process, and nothing says so

**Status:** open, found 2026-08-14 while building `bpmn-call-activity-decomposition/springboot`.

**What happens.** A call activity starts a new process instance. On Camunda 7 that instance
has no business key unless the model asks for one, and the business key is where the
Camunda 7 adapter keeps the ID of the workflow aggregate. The first task of the called
process therefore looks up an aggregate with no ID:

```
Error while evaluating expression: ${checkCollateral}. Cause: The given id must not be null
```

That is Spring Data speaking, through the engine, on a job which then retries and ends in an
incident. Neither the call activity nor the business key nor VanillaBP is mentioned.

**Reproduction.** In `bpmn-call-activity-decomposition/springboot`, remove

```xml
<camunda:in businessKey="#{execution.processBusinessKey}" />
```

from the call activity of `loan_approval.bpmn` and run `LoanApprovalIT`.

**Where it lands.** VanillaBP already rewrites the BPMN of a workflow module while deploying
it: it injects listeners, and the Camunda 7 adapter rewrites `calledElement` for name-clash
avoidance. Adding the business key propagation to a call activity is the same kind of
change, made in the same place, and it would make the difference between the two engines
disappear for good - Camunda 8 propagates parent variables by default, so the aggregate's ID
arrives there without anything being modelled.

The fallback, if injecting is not wanted, is a check at deployment: a call activity without
business key propagation is a defect on this engine every time, and the message can name the
call activity and the line to add.

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

**Status:** open, found 2026-08-15 when `bpmn-boundary-events/springboot` failed once in CI on
Camunda 7 and passed on the rerun. Related to G3 and G4, but a different collision: this one
is on the BPMS' own entity, not on the workflow aggregate.

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
