![Header](./readme/vanillabp-headline.png)

# An idle application

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

A workflow application spends most of its life waiting. Waiting used to cost money. The
application asked its database every few seconds whether anything had turned up, all day and
all night. A database billed by what it does bills for that. Two configuration keys stop the
asking. This blueprint sets them. It also counts the statements, so nobody has to take the
saving on trust.

## What this blueprint shows

![The loan approval process, waiting in a timer before the money is paid out](docs/loan_approval.png)

The loan approval of the base blueprint with one step added. The money is paid out when the
withdrawal period of the contract is over. So the workflow waits in a timer between the rating
and the payment. Here it waits thirty seconds, because the test of this blueprint has to
finish. In a real contract it would be two weeks, and nothing written below changes with that
number.

While the workflow waits, this application sends its database nothing at all.

The first of the two keys is `vanillabp.outbox.poll-interval` in
`application/src/main/resources/application.yaml`. VanillaBP dispatches an outbox entry right
after the transaction which wrote it. It also polls the store, as a safety net for entries no
such notification reached. The poller waits for the earliest entry which is due. This key says
how long it may wait when nothing is due at all. The default is ten seconds, which makes 8640
questions a day about work nobody created. This blueprint sets an hour.

The second is `vanillabp.adapters.camunda7.sleep-until-something-is-due` in
`application/src/main/resources/application-camunda7.yaml`, and it belongs to the embedded
engine. Without it the engine keeps asking its database for work while nothing happens. With
it, a cycle which found nothing asks once when the next job is due and waits for that moment.
Any transaction which writes a job wakes it again. The adapter's
[wiki](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter/wiki/Configuration#letting-an-idle-engine-go-quiet)
says what the key costs and which database index it needs from you.

One key without the other saves nothing. If the engine waits but the outbox stays at its
default, the database is still asked every ten seconds, just as before. That is why this
blueprint names both. And if you set one of them and see no saving, the feature is not broken.

### Counting it yourself

`GET /api/loan-approval/statements` returns how many SQL statements the database has received.
Open it, wait, and open it again. The difference is what this application costs while nothing
happens.

Measured on this blueprint with both keys set, an application waiting with nothing to do:

```
640 statements so far
... a minute later ...
640 statements so far
```

The same application with the two keys taken out. This is what an application which sets
nothing does:

```
640 statements so far
... a minute later ...
712 statements so far
```

Seventy-two statements a minute is the cost once the application has been idle long enough for
the engine to widen its cycle to the full minute. Right after some work the same minute costs
more: 167 here in the minute after the loan was paid out. The reason is that the engine starts
its backoff at five seconds again. So a day of doing nothing costs a hundred thousand
statements or more. On a database billed by what it does, you pay for every one of them.

Almost all of those come from the outbox poller, not from the engine: six polls a minute, each
one a select, a delete and the commits around them. The engine's share is one statement a
minute once it has backed off. In the minutes after some work it is the bigger half.

The number is the number of lines in `target/loan-approval.trace.db`. H2 writes that file
because the JDBC URL says `TRACE_LEVEL_FILE=2`, one line per statement. Open it to see what the
database was asked, not only how often. Counting lines in a file costs the database nothing, so
asking does not disturb the measurement. `DatabaseTraffic` is the only class here which another
database would change. It is measuring equipment, not something an application needs.

### Which engine shows which half

The outbox half is the same on every BPMS (business process management system). It is
VanillaBP's own machinery, in the database of the application.

The engine half exists only where the engine runs inside the application. Of the supported
engines, that is Camunda 7. A remote BPMS does its polling in its own cluster. So on Camunda 8
the second key has no place to go, and there is nothing of the engine in your database to save.
Running this blueprint with `-Pcamunda8` therefore shows one half. The number it prints is the
outbox half alone.

## Delta to the base blueprint

Compared to [`module-single`](https://github.com/vanillabp-blueprints/module-single-quarkus):

|            File             |                                    What is different                                    |
|-----------------------------|-----------------------------------------------------------------------------------------|
| `application.yaml`          | `vanillabp.outbox.poll-interval`, and a JDBC URL which has H2 write down its statements |
| `application-camunda7.yaml` | `sleep-until-something-is-due` for the embedded engine                                  |
| `loan_approval.bpmn`        | a timer catch event for the withdrawal period and a service task behind it              |
| `DatabaseTraffic.java`      | counts the statements H2 wrote into its trace file                                      |
| `ApiController.java`        | one more endpoint, which returns that number                                            |
| `WorkflowTaskHandler.java`  | the `@WorkflowTask` method of the task behind the timer                                 |
| `Service.java`              | pays out the loan                                                                       |
| `Aggregate.java`            | `paidOut`                                                                               |
| `LoanApprovalIT.java`       | no statement while the workflow waits, and the workflow going on when the timer is due  |

## Running it

Requires a JDK 21 or newer. Camunda 7 is embedded, so nothing else has to run:

```bash
mvn install verify
```

To run it on another BPMS, pick another Maven profile. Not one line of Java changes:

```bash
mvn install verify -Pcamunda8
```

Camunda 8 is a remote engine, so a cluster has to run. Its address and everything else specific
to that engine are in its profile file
`application/src/main/resources/application-camunda8.yaml`. The module's own test has a copy:

```yaml
vanillabp:
  adapters:
    camunda8:
      # Camunda 8 is a remote engine: point this at your cluster.
      rest-address: http://localhost:8080
      # Camunda 8.10 can lose a leased job for a whole job-timeout. Until Camunda fixes that,
      # the adapter advises against a lease. The key has no default:
      # https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/wiki/Configuration#a-job-somebody-else-holds
      job-lease: do-not-use
```

The blueprint does not lease its jobs. On 8.10 the gateway of the cluster can lose a leased
job when a long poll ends, and the job then waits for a whole `job-timeout`. Until Camunda
fixes that, the adapter advises `do-not-use`, and moving to `use` later costs nothing. Why is
[decision 88 of the Camunda 8 adapter](https://github.com/camunda-community-hub/vanillabp-camunda8-adapter/blob/main/DECISIONS.md#88-until-camunda-fixes-its-gateway-the-adapter-advises-against-a-lease-and-its-own-tests-keep-one).

The file applies because the Maven profile `camunda8` makes the profile of the same name the
parent of the profile the application runs in. So you choose the engine once, on the Maven
command line, and the build, the tests and the run all follow it. The file with the engine key
of this blueprint, `application-camunda7.yaml`, applies the same way, and only while Camunda 7
is the engine.

[Operaton](https://operaton.org), the open source successor of Camunda 7, is embedded as well
and needs nothing else either. The BPMN files are the ones of Camunda 7, because Operaton reads
their `camunda:` attributes:

```bash
mvn install verify -Poperaton
```

[CIB seven](https://cibseven.org), a fork of Camunda 7 made by CIB software, is embedded as
well and needs nothing else either. The BPMN files are the ones of Camunda 7, because CIB seven
reads their `camunda:` attributes:

```bash
mvn install verify -Pcibseven
```

Start the application:

```bash
mvn -pl application quarkus:dev
```

The startup log says what this application still costs while it is quiet. It is worth reading
once:

```
'vanillabp.outbox.poll-interval' is PT1H instead of the default PT10S, so an outbox sleeps
until its next entry is due and an application with nothing to do sends it no statement at
all. What is still awake:
- the retention cleanup of the task-delivery records looks once per hour, and only after a
  delivery was recorded, so an application which records nothing does not wake it
- the gauge of the entries waiting in an outbox ('vanillabp.outbox.pending') counts them
  while your monitoring scrapes, at most once per 'vanillabp.metrics.gauge-cache', so a
  dashboard somebody left open keeps that one statement going
...
Camunda7[camunda7]: the job executor sleeps until the next job is due instead of polling
every 5 to 60 seconds, and a transaction which writes a job wakes it. Jobs are acquired by
due date now, which is not the engine's default, so add a database index on the due date of
the ACT_RU_JOB table.
Camunda7[camunda7]: the engine's metrics reporter is switched off. It would have written to
the database every 900 seconds and woken this engine four times an hour.
```

Now walk through it. Start a loan approval, which is the only URL you need:

```
http://localhost:8080/api/loan-approval/start?amount=5000
```

The rating is done at once and then the workflow waits:

```
Loan approval '0f7c…' started
Credit rating of loan approval '0f7c…' is 50
The loan is paid out after the withdrawal period. Until then nothing of this application runs, which this URL shows -> http://localhost:8080/api/loan-approval/statements
```

Thirty seconds later the timer is due and the task behind it runs. This is the half nobody may
trade away for the quiet:

```
Loan approval '0f7c…' was paid out
```

From here the application has nothing left to do. Ask what its database has done so far:

```
http://localhost:8080/api/loan-approval/statements
```

Wait a minute, ask again, and compare the two numbers. The file behind them is
`application/target/loan-approval.trace.db`. It is worth a look: every line is one statement.
Without the two keys the same handful of them repeats every ten seconds for as long as the
application runs.

Measure after the application has settled, not in its first minute. A start goes on for a while
after it says it is ready. The schema, the deployment and the first check of the retention of
the delivery records are all work. They are not traffic of an idle application, so do not count
them.

To see the other side, put `poll-interval: PT10S` back into `application.yaml`. Set
`sleep-until-something-is-due: false` in `application-camunda7.yaml` and start the application
again. Ask the same question twice, a minute apart. The number grows by what the two pollers
do.

What the application is waiting for is in its own database. The workflow aggregate says how far
the business case has come. The endpoint above says what the database is asked in the meantime.

## How it works

|                                          File                                          |                                    Role                                     |
|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `application/src/main/resources/application.yaml`                                      | the limit on how long an outbox poller may wait while nothing is due        |
| `application/src/main/resources/application-camunda7.yaml`                             | the key which lets the embedded engine wait for the next due job            |
| `loan-approval/src/main/resources/loan-approval/processes/camunda7/loan_approval.bpmn` | the timer the workflow waits in                                             |
| `.../loanapproval/DatabaseTraffic.java`                                                | counts the lines H2 wrote into its trace file, which is measuring equipment |
| `.../loanapproval/ApiController.java`                                                  | the endpoint returning the count                                            |
| `.../loanapproval/WorkflowTaskHandler.java`                                            | the method behind the timer, called when the due date has come              |
| `loan-approval/src/test/.../LoanApprovalIT.java`                                       | the proof: nothing while it waits, and the payment afterwards               |

The saving comes from a property and the shape of the deployment, not from a line of code. So
there is almost nothing to read in the Java. What is worth reading is what the two keys do not
do.

The limit on the outbox is not a notification between nodes. Nothing in VanillaBP wakes another
node for ordinary work. The node which writes an entry is awake anyway, because its own commit
dispatches the entry. A resting node which never hears about that write loses nothing. The
limit covers the one case which is left: a node which wrote work down and then went away before
doing it. An hour is a sensible value for that, seconds are not. If you take the key for a
heartbeat, you set it to seconds and give the whole saving away.

The engine key has no limit, because the due date answers the question exactly. The exception
is a job which another application wrote into the same engine. That commit happens in another
process and reaches nothing here. So this does not cover two applications on one engine.

Something is still awake, and the startup message says so instead of promising silence. The
retention cleanup of the task delivery records runs once an hour, if a delivery was recorded
since the last run. A monitoring system which reads the gauge of the entries waiting in an
outbox asks the database for that number. The engine's own metrics reporter would write its
counters every 900 seconds. So it stops writing them where the engine may wait. An application
which wants them in its database sets `db-metrics-reporting: true` and pays four wake-ups an
hour for it.

One thing is left to whoever owns the database. Where the engine waits for due dates, it also
acquires jobs by due date. That order needs an index on the due date of `ACT_RU_JOB`. The
startup message asks for it. An in-memory database does not care, a production one does.

The application itself keeps running while it waits. Nothing here makes the process go away.
What it would take to scale a workload of this kind down to nothing is a separate question.

The longer the pollers rest, the harder it gets to tell a quiet application from a stuck one.
The metrics tell them apart. The wiki page about observability says which counter answers which
question.

## Documentation

- [Observability](https://github.com/vanillabp/adapter-platform-integration/wiki/Observability): what a quiet application looks like from outside, and how to see that it is quiet rather than stuck
- [Running more than one node](https://github.com/vanillabp/adapter-platform-integration/wiki/Running-more-than-one-node): what a second node changes, which is the case the cap on the outbox exists for
- [Quarkus integration](https://github.com/vanillabp/adapter-platform-integration/wiki/Quarkus-integration): the phase-two outbox, its store and its configuration
- [BPMS adapters](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-adapters): which engine runs inside the application and which one runs elsewhere
- the wiki of the BPMS adapter you use, for the keys of that engine

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
