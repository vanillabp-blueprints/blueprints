-- The schema of THIS workflow module: the table of its workflow aggregate and nothing else.
-- It travels in the module's JAR, so whoever adds the module to an application gets its
-- migrations with it.
--
-- The file lives below 'loan-approval/', the directory named after the workflow module ID, for
-- the same reason the BPMN files do: all modules share one classpath, and two modules with a
-- file called 'db/migration/V1.0.0__...sql' would overwrite each other.
--
-- The version is the module's own, and it is applied into a history table of the module's own.
-- Flyway knows one timeline per history table and nothing else: it has no notion of who wrote
-- a migration, so a shared history would mean the module and the application competing for
-- version numbers.
CREATE TABLE LOAN_APPROVAL (
    LOAN_REQUEST_ID VARCHAR(255) NOT NULL,
    AMOUNT INT,
    CREDIT_RATING INT,
    CONSTRAINT PK_LOAN_APPROVAL PRIMARY KEY (LOAN_REQUEST_ID)
);
