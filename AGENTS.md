# Working on the blueprints monorepo

This file is for agents which change this repository. The rules a blueprint follows, the checks
CI runs and how a blueprint reaches its own repository are in [`CONTRIBUTING.md`](./CONTRIBUTING.md).
Read it before you change anything.

Each blueprint directory has an `AGENTS.md` of its own. That one is for a different reader: an
agent which copies the blueprint into an application. Its sections are fixed, and
`bin/check_docs_structure.py` checks them.

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

