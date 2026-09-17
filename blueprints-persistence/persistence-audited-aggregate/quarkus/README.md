![Header](./readme/vanillabp-headline.png)

# A workflow aggregate which remembers every change

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

Banks and insurers have to be able to say later who changed a case, when, and what it looked
like before. The workflow aggregate is the right place for that, because it carries the case.

VanillaBP asks nothing of you here and hands you nothing: auditing belongs to the
application, and it stays that way. What VanillaBP does have is the seam. An entry which
reports something to another system is written inside the transaction of the event and sent
afterwards, so by the time it is sent the case has moved on. Such an entry may say which
state it means, and the application is the one which knows how to name a state and how to
read it back.

This blueprint fills that seam with Hibernate Envers.

## What this blueprint shows

![The loan approval process: a credit rating, a decision by a person, and the payout](docs/loan_approval.png)

The loan approval of the base blueprint with three moments in it, which is what makes the
difference visible. A service task writes the credit rating, a person decides whether the
risk is acceptable, and a service task after that pays the loan out. Each of the three
writes the workflow aggregate, so each of them is one state of the case.

Two things happen on top of that. Every change is recorded by Hibernate Envers, and the
decision is reported to a compliance archive through the outbox VanillaBP already runs.
The archive is away when the notice first arrives, so the notice waits, the payout is
booked meanwhile, and what the archive is finally handed is the loan approval as it was at
the decision rather than as it is by then.

### What the auditing writes

One word on the aggregate, `@Audited`, and Hibernate Envers keeps three tables instead of
one:

|          Table           |                                What is in it                                |
|--------------------------|-----------------------------------------------------------------------------|
| `LOAN_APPROVAL`          | the loan approval as it is now, the table every blueprint has               |
| `LOAN_APPROVAL_AUD`      | one row per change, with the number of the change and what kind it was      |
| `LOAN_APPROVAL_REVISION` | one row per change, with the moment it happened and the person it came from |

The last table is the revision entity of this application, `AuditedChange`. Without one,
Envers writes a table of its own called `REVINFO` which knows the number and the moment and
nothing else. Adding a class and a listener is what gets the name of the person in, and that
name is usually the reason an auditing is bought at all.

Reading it is a query of Envers' own API, `forRevisionsOfEntity`, which answers one row per
change with the state and the revision behind it. The API of this blueprint shows the
result:

```
#1 by the customer: Aggregate(loanRequestId=0f7c…, amount=5000, creditRating=null, …)
#2 by the process: Aggregate(…, creditRating=50, assessRiskTaskId=null, …)
#3 by the process: Aggregate(…, creditRating=50, assessRiskTaskId=22, …)
#4 by paula: Aggregate(…, riskAcceptable=true, decidedBy=paula, paidOut=null)
#5 by the process: Aggregate(…, riskAcceptable=true, decidedBy=paula, paidOut=true)
```

Five changes for three business steps, and the third one is the reason: the application
keeps the id of the open user task on the aggregate, which is a write like any other. An
auditing records what the data does, not what the process means.

### This is Envers, not a last-changed stamp

Auditing is a word two different things go by, and the other one is close enough to cause a
wrong decision. A framework auditing with annotations like `@CreatedDate` and
`@LastModifiedDate` writes who touched a record last and when they did. It keeps no old
state, so it can never answer what the case looked like at an earlier moment. Hibernate
Envers keeps the old states, and that is what this blueprint is about.

### The revision has to exist before the flush

This is the part worth reading before copying anything.

Envers hands out the number of a change when the transaction is flushed. An outbox entry is
written before that, inside the business transaction, so an entry which wants to name the
state it saw would have to name a number nobody has yet. The way out is one line in
`AuditedAggregatePersistence`:

```java
final var revision = AuditReaderFactory
    .get(entityManager)
    .getCurrentRevision(AuditedChange.class, true);
```

The second argument writes the revision row right away, and every change of this transaction
is then recorded under exactly that number. So the notice can carry it, and the dispatch can
ask for it later. The number belongs to the transaction rather than to a single write, which
is what makes it usable: a transaction which flushes twice still has one number, and the
notice stays right.

That method carries a deprecation which points at `RevisionListener`, and the pointer does
not lead anywhere for this question: a listener runs while the transaction commits, which is
after the notice was written. Envers has announced a replacement since its version 5.2 and
has not shipped one. So the call is used here, with the warning suppressed on that one
method and the reason written next to it, rather than left to show up in every build of
everybody who copies this.

There is a second way, and it fits an application which uses optimistic locking already: the
`@Version` attribute of the aggregate names a state as well, it is there without asking
anybody, and the load then reads the audit row whose version matches. This blueprint takes
the revision instead, because it asks nothing of the application. Take the version where you
have one, and keep in mind that it says nothing about who made the change, which is the other
half of what an auditing is for.

A transaction which asks for the revision and then changes nothing leaves a revision row
behind which no audit row points at. That is a row, not a problem, and it is the price of
knowing the number early.

### Who made the change

Envers builds the revision entity itself, without asking the bean container, so the name of
the person cannot be injected into the listener. It travels on the thread: `ChangeAuthor`
holds it, the API binds it around the call, and the listener reads it when Envers writes the
revision.

Where that binding sits matters. Envers writes the revision when the transaction commits,
which is after the business method returned, so a name taken back inside the business method
comes too late and the change ends up unattributed. The API is the right place, and an
application with a security framework reads the authenticated user in a filter, which spans
the same stretch.

Changes nobody announced come from the process: a service task runs on a thread of the BPMS,
where there is no person to name.

### What the notice reports, and what it cannot

The notice is an operation of the application in VanillaBP's outbox, namespaced
(`loan-approval:COMPLIANCE_NOTICE`) so it can never collide with VanillaBP's own. It is
written down inside the transaction of the decision, which means it cannot survive a rollback
of that decision, and it is sent after the commit. `askingForTheStateOfTheEvent` is what makes
it report the state of the decision:

```java
PhaseTwoCall
    .of(NOTIFY_THE_ARCHIVE, WORKFLOW_MODULE_ID, BPMN_PROCESS_ID, loanRequestId, null, args)
    .askingForTheStateOfTheEvent(loanApprovals.getAuditingId(loanApproval));
```

An entry which asks for nothing reads the aggregate as it is when it is dispatched, and that
is what the entries of VanillaBP itself do. They write into the BPMS, the BPMS is where the
case goes on, and a value which is a day old would be wrong there. Asking on one of them is
refused rather than ignored.

The outbox itself is VanillaBP's, and an operation of an application goes into it the same
way an extension's does: a namespaced name, an idempotency key of its own, and a dispatch
which no adapter is involved in. An application which already runs an outbox of its own uses
that one; what this blueprint needs from it is only that the entry is written in the
transaction of the event and sent after the commit.

Limits belong to the picture as well:

- The state may be gone. An auditing is cleaned up at some point, and an entry may wait
  longer than that. The load then answers nothing, VanillaBP reads the current state instead
  and writes a warning naming the aggregate and the state it wanted. A report with newer
  values beats no report.
- Only what is in the aggregate is audited. What an adapter reads out of the BPMS while it
  dispatches, the assignee of a user task, its candidates, its due date, is the state of that
  moment. It is the data of the BPMS, and the BPMS keeps no history of it anybody could ask.
- MongoDB has nothing of this built in. An application storing its aggregates there writes
  the versions itself or does without them, and the two methods of the seam are the same two
  either way.

Auditing is not free. Every change costs a second write and a row which is never deleted, so
it is switched on where somebody has to answer for the case later and left off everywhere
else.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|                   File                   |                                            What is different                                             |
|------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `loan_approval.bpmn`                     | a decision by a person between the credit rating and the payout, so the aggregate is written three times |
| `model/Aggregate.java`                   | carries `@Audited`, and the attributes the three steps write                                             |
| `audit/AuditedChange.java`               | the revision entity, so a change knows who made it                                                       |
| `audit/ChangeAuthor.java`                | where that name comes from, and how long it has to be there                                              |
| `audit/AuditedAggregatePersistence.java` | the seam: the state of now, and the aggregate as it was                                                  |
| `audit/ComplianceNotices.java`           | the outbox operation of the application, asking for the state of its event                               |
| `ComplianceArchive.java`                 | the port to the archive, so a test can put a simulator in its place                                      |
| `LoanApprovalIT.java`                    | decides, waits for the payout, and asserts on what the archive was told                                  |

## Running it

Requires a JDK 21. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

Running it on another BPMS is a Maven profile, not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run. Start one; its address, and everything
else specific to that engine, lives in its profile file
`application/src/main/resources/application-camunda8.yaml`, with a copy for the module's own
test:

```yaml
vanillabp:
  adapters:
    camunda8:
      # Camunda 8 is a remote engine: point this at your cluster.
      rest-address: http://localhost:8080
```

That file is loaded because the Maven profile `camunda8` makes the config profile of the same
name the parent of whichever profile the application runs in, so the engine is chosen once, on
the Maven command line, and the build, the tests and `quarkus:dev` all follow it.

Start the application:

```bash
mvn -pl application quarkus:dev
```

Start a loan approval. This is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000&requestedBy=the%20customer
```

It answers with the id of the loan request, and the log shows where the process stops:

```
Loan approval '0f7c…' was requested by the customer
Credit rating of loan approval '0f7c…' is 50
Loan approval '0f7c…' waits for a risk assessment. Continue with one of:
  Acceptable -> http://localhost:8080/api/loan-approval/0f7c…/assess-risk/1f2e…?riskIsAcceptable=true&decidedBy=paula
  Too risky  -> http://localhost:8080/api/loan-approval/0f7c…/assess-risk/1f2e…?riskIsAcceptable=false&decidedBy=paula
```

Open one of them, and the log shows the point of the whole blueprint:

```
Risk of loan approval '0f7c…' was assessed by paula as acceptable
The compliance archive does not answer about loan approval '0f7c…'. VanillaBP keeps the notice and tries again.
Loan approval '0f7c…' was paid out
The compliance archive recorded 'risk-assessed' of loan approval '0f7c…': Aggregate(…, riskAcceptable=true, decidedBy=paula, paidOut=null)
```

The archive is handed the loan approval without the payout, although the payout was booked
before the notice went out. The refusal at the first attempt is arranged, in
`LocalComplianceArchive`, so the wait happens on the first run instead of on the first bad
day.

Two URLs show the two answers side by side, the case as it is and the trail of everything it
was:

```
http://localhost:8080/api/loan-approval/0f7c…
http://localhost:8080/api/loan-approval/0f7c…/trail
```

## How it works

|                                          File                                          |                                              Role                                              |
|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the process: rating, decision, payout, and therefore three states of the case                  |
| `.../loanapproval/model/Aggregate.java`                                                | the workflow aggregate, audited by one annotation                                              |
| `.../loanapproval/model/AggregateRepository.java`                                      | the repository, which knows nothing about revisions                                            |
| `.../loanapproval/audit/AuditedChange.java`                                            | the revision entity: number, moment, and who made the change                                   |
| `.../loanapproval/audit/ChangeAuthor.java`                                             | the listener filling that name in, and where the name comes from                               |
| `.../loanapproval/audit/AuditedAggregatePersistence.java`                              | what VanillaBP asks: the state of now, and the aggregate as it was at a state                  |
| `.../loanapproval/audit/ComplianceNotices.java`                                        | the notice: planned in the transaction of the decision, sent afterwards, asking for that state |
| `.../loanapproval/ComplianceArchive.java`                                              | the port to the archive; `LocalComplianceArchive` is the stand-in to replace                   |
| `.../loanapproval/Service.java`                                                        | the business code, which knows nothing about revisions                                         |
| `.../loanapproval/ApiController.java`                                                  | the GET endpoints, and the one place saying who is acting                                      |
| `loan-approval/src/test/.../ComplianceArchiveSimulator.java`                           | the archive in the test, away at the first attempt                                             |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | plays the case through and asserts what the archive was told                                   |

The test is the proof. It starts a loan approval, answers the risk assessment as `paula`,
waits until the payout was booked, and only then looks at what the archive received. The
notice carries the decision and no payout, while the aggregate in the database carries both,
and the archive was asked twice because the first attempt was turned down. The second test
reads the trail and checks that every change names its author.

An application which keeps no auditing at all is unaffected by any of this. Both methods of
the seam have defaults, and both defaults are what VanillaBP did before they existed: no
state is named, and every load reads the current one.

## Documentation

- [Workflow aggregates](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates): what an aggregate is, and why there are no process variables
- [Aggregate persistence](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates#aggregate-persistence): the interface this blueprint implements, and when an application needs to
- [What the outbox guarantees](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration#what-the-outbox-guarantees): why an entry is written first and sent afterwards, and what that costs
- [Workflow modules](https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules): what a workflow module is, its ID, and where its BPMN files are looked for
- [Wire up a process / Wire up a task](https://github.com/vanillabp/spi-for-java#usage): the annotations used in `WorkflowTaskHandler.java`
- [Hibernate Envers](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#envers): the auditing itself, its tables and its queries
- [The Envers extension](https://quarkus.io/guides/hibernate-orm#envers): what the extension adds and what it can be configured with
- the wiki of the [BPMS adapter](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters) you use: how a BPMN task has to be modelled for that engine

This blueprint is developed in the monorepo
[`blueprints`](https://github.com/vanillabp-blueprints/blueprints). This repository is a
read-only mirror, **issues and pull requests belong there.**

## Noteworthy & Contributors

[VanillaBP](https://www.github.com/vanillabp/spi-for-java) was developed by [Phactum](https://www.phactum.at) with the
intention of giving back to the community as it has benefited the community in the past.

![Phactum](./readme/phactum.png)

## License

Copyright 2026 Phactum Softwareentwicklung GmbH

Licensed under the Apache License, Version 2.0
