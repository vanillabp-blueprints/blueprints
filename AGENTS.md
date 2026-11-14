# Working on the blueprints monorepo

This file is for agents which change this repository. The rules a blueprint follows, the checks
CI runs and how a blueprint reaches its own repository are in [`CONTRIBUTING.md`](./CONTRIBUTING.md).
Read it before you change anything.

Each blueprint directory has an `AGENTS.md` of its own. That one is for a different reader: an
agent which copies the blueprint into an application. Its sections are fixed, and
`bin/check_docs_structure.py` checks them.

## A workflow module brings no adapter

A workflow module depends on `io.vanillabp:vanillabp-spring-boot-support` or
`io.vanillabp:vanillabp-quarkus-support`. If its user tasks are shown in the Business Cockpit,
it also depends on `io.vanillabp.businesscockpit:business-cockpit-spring-boot-support`. These
libraries know no BPMS.

An adapter is a different thing. It connects the module to one BPMS or to the cockpit for one
BPMS, for example `camunda8-adapter-spring-boot` or
`businesscockpit-camunda8-adapter-spring-boot`. A workflow module names an adapter only in the
test scope, because its tests have to run it. Outside the tests, the application brings the
adapters, once for all its modules.

`io.vanillabp:vanillabp-quarkus-integration` is treated like an adapter. It is the Quarkus
extension of VanillaBP, and a Quarkus extension belongs to the application. A workflow module
on Quarkus names it only in the test scope, because its tests boot VanillaBP.

The build checks this. The POM of each blueprint runs the Maven Enforcer rule
`no-adapter-in-a-workflow-module` for its modules. It fails if a module names an adapter or
the Quarkus extension outside the test scope, directly or through another dependency. The
application module switches the rule off, because it is where the adapters belong. There is no shared parent POM,
so every blueprint carries its own copy of the rule (see Rule 1 in `CONTRIBUTING.md`). A new
blueprint copies the rule with the rest of its POM.

One blueprint is the exception: `module-standalone`. There the application is the workflow
module, so it names the adapters like any application, and it has no such rule.

## The service class says `@Transactional`

Put `@Transactional` on the class of every business service in a workflow module, instead of
on its methods. A method which throws a `TaskException` gets
`@Transactional(noRollbackFor = TaskException.class)` on Spring Boot and
`@Transactional(dontRollbackOn = TaskException.class)` on Quarkus. Without it the transaction of
the task is marked for rollback, and VanillaBP fails the task. Never annotate the task handler.
Rule 6 in `CONTRIBUTING.md` says why, and names the one exception, `persistence-custom`.

## How we write

Most people who read this repository read English as a second language, and so does the
maintainer. Long sentences, rare words and stacked nouns slow them down. Write so that
nobody has to read a sentence twice.

Short main sentences, one thought each. One subordinate clause is enough. Active voice.
The common word instead of the rare one: `use` instead of `leverage`, `about` instead of
`regarding`, `so` instead of `consequently`, `has` instead of `possesses`. A technical term
stays a technical term, but say what it means the first time it turns up, and write an
abbreviation out once. If a sentence trips you up when you read it aloud, rewrite it.

This holds for every English text here: the README files, the `AGENTS.md` files of the
blueprints, `CONTRIBUTING.md`, this file, the Javadoc and comments which explain something, in
the main code and in the tests, and the texts of commits and pull requests. This repository has no wiki of its own. If it gets one,
the rule holds there as well, because a wiki clone has no `AGENTS.md` of its own.

Nothing a program reads is renamed for the sake of language. Class and method names,
configuration keys, artifact coordinates and the `##` headings of the blueprint documents stay
as they are, because code, tests, checks and other repositories point at them.

Before and after, from this repository:

> What makes it more than a swapped repository is the count: a workflow needs **three** stores,
> and all three of them move.

becomes

> It is more than a swapped repository, because of the count. A workflow needs **three**
> stores, and all three of them move.

That one is from the README of `persistence-mongodb`. The next is from `CONTRIBUTING.md`:

> A BPMS-specific file is always shipped and only sometimes loaded: the file of one engine sits
> in the JAR of a build for another one as well, where it does nothing.

becomes

> A file for one BPMS is always shipped and only sometimes loaded. The file of one engine is
> also in the JAR of a build for another one, where it does nothing.

And one from the README of `bpmn-signals`:

> Today's interest rate is published, and every loan approval waiting for it carries on - one, a
> dozen, or none, and the publisher knows nothing about them.

becomes

> Today's interest rate is published, and every loan approval which waits for it goes on. That
> may be one, a dozen, or none, and the publisher knows nothing about them.

