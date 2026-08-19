-- The outbox table of the Spring Boot integration, and the one place in this blueprint where an
-- application has to write down somebody else's schema.
--
-- On this platform VanillaBP's phase-two outbox is gruelbox
-- (com.gruelbox:transactionoutbox-core), which brings its own migrator. VanillaBP deliberately
-- ships no statements for TXNO_OUTBOX. The switch 'vanillabp.outbox.create-schema' covers both,
-- though: turning it off so that Flyway can own VanillaBP's tables turns gruelbox's migrator off
-- as well, so this application has to create that table itself.
--
-- Where these statements come from: gruelbox's migrator was let loose on an empty H2 database
-- once and the result was read back out of it. They are NOT hand-written from gruelbox's source,
-- where the schema is thirteen migrations, some of them in MySQL syntax, several overridden per
-- dialect. GruelboxSchemaDriftTest compares what is written here against what that migrator
-- produces, so a gruelbox upgrade which adds a column fails a build instead of a deployment.
--
-- TXNO_VERSION is not created here. It is the bookkeeping of gruelbox's migrator, and the
-- migrator is off. TXNO_SEQUENCE is created, because gruelbox reads it as soon as an entry is
-- ordered within a topic.
--
-- The column names are gruelbox's, spelled the way gruelbox spells them and not quoted, so every
-- database folds them the same way it folds them for gruelbox itself.
CREATE TABLE TXNO_OUTBOX (
    id VARCHAR(36) NOT NULL,
    invocation CLOB,
    lastAttemptTime TIMESTAMP,
    nextAttemptTime TIMESTAMP,
    attempts INT,
    blocked BOOLEAN,
    version INT,
    uniqueRequestId VARCHAR(250) UNIQUE,
    processed BOOLEAN,
    topic VARCHAR(250) DEFAULT '*' NOT NULL,
    seq BIGINT,
    CONSTRAINT PK_TXNO_OUTBOX PRIMARY KEY (id)
);

CREATE INDEX IX_TXNO_OUTBOX_1 ON TXNO_OUTBOX (processed, blocked, nextAttemptTime);

CREATE INDEX IX_TXNO_OUTBOX_2 ON TXNO_OUTBOX (topic, processed, seq);

CREATE TABLE TXNO_SEQUENCE (
    topic VARCHAR(250) NOT NULL,
    seq BIGINT NOT NULL,
    CONSTRAINT PK_TXNO_SEQUENCE PRIMARY KEY (topic, seq)
);
