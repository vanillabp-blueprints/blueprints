-- The outbox table of the Spring Boot integration, and the one place in this blueprint where an
-- application has to write down somebody else's schema.
--
-- On this platform VanillaBP's phase-two outbox is gruelbox
-- (com.gruelbox:transactionoutbox-core), which brings its own migrator. VanillaBP deliberately
-- ships no statements for TXNO_OUTBOX. The switch 'vanillabp.outbox.create-schema' covers both,
-- though: turning it off so that Flyway can own VanillaBP's tables turns gruelbox's migrator off
-- as well, so this application has to create that table itself.
--
-- Where these statements come from: gruelbox writes them itself.
--
--   DefaultPersistor.builder().dialect(Dialect.H2).build().writeSchema(writer)
--
-- emits every migration of the library as SQL for the dialect it is given, and that is what
-- follows, in gruelbox's own order and with its own numbering. The two migrations which do
-- nothing on H2 keep their numbers, 8 and 13, so this file stays comparable to the library.
-- GruelboxSchemaDriftTest asks gruelbox for the same output on every build and compares, so an
-- upgrade which adds or changes a migration fails a build instead of a deployment.
--
-- The statements are gruelbox's, spelled the way gruelbox spells them: unquoted column names, and
-- a rename of 'blacklisted' to 'blocked' where the library renamed it. Reading them replays how
-- the schema of that library grew, which is more than a single CREATE TABLE would say and exactly
-- as much as the library guarantees.
--
-- H2 is the dialect this blueprint runs on. An application on another database asks writeSchema
-- for that dialect and gets its statements, which is why nothing here is hand-written.
--
-- TXNO_VERSION is not created here. It is the bookkeeping of gruelbox's migrator, and the migrator
-- is off. writeSchema does not emit it either.

-- 1: Create outbox table
CREATE TABLE TXNO_OUTBOX (
    id VARCHAR(36) PRIMARY KEY,
    invocation TEXT,
    nextAttemptTime TIMESTAMP(6),
    attempts INT,
    blacklisted BOOLEAN,
    version INT
);

-- 2: Add unique request id
ALTER TABLE TXNO_OUTBOX ADD COLUMN uniqueRequestId VARCHAR(100) NULL UNIQUE;

-- 3: Add processed flag
ALTER TABLE TXNO_OUTBOX ADD COLUMN processed BOOLEAN;

-- 4: Add flush index
CREATE INDEX IX_TXNO_OUTBOX_1 ON TXNO_OUTBOX (processed, blacklisted, nextAttemptTime);

-- 5: Increase size of uniqueRequestId
ALTER TABLE TXNO_OUTBOX ALTER COLUMN uniqueRequestId VARCHAR(250);

-- 6: Rename column blacklisted to blocked
ALTER TABLE TXNO_OUTBOX RENAME COLUMN blacklisted TO blocked;

-- 7: Add lastAttemptTime column to outbox
ALTER TABLE TXNO_OUTBOX ADD COLUMN lastAttemptTime TIMESTAMP(6) NULL AFTER invocation;

-- 9: Add topic
ALTER TABLE TXNO_OUTBOX ADD COLUMN topic VARCHAR(250) NOT NULL DEFAULT '*';

-- 10: Add sequence
ALTER TABLE TXNO_OUTBOX ADD COLUMN seq BIGINT NULL;

-- 11: Add sequence table
CREATE TABLE TXNO_SEQUENCE (topic VARCHAR(250) NOT NULL, seq BIGINT NOT NULL, PRIMARY KEY (topic, seq));

-- 12: Add flush index to support ordering
CREATE INDEX IX_TXNO_OUTBOX_2 ON TXNO_OUTBOX (topic, processed, seq);
