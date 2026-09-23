package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import liquibase.change.core.CreateTableChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * What this blueprint is about: every table exists, and none of them was created by
 * VanillaBP, by Hibernate or by the engine.
 *
 * <p>
 * That nothing created them at runtime is not asserted here but configured:
 * {@code vanillabp.outbox.create-schema} is off, {@code ddl-auto} is {@code validate} and
 * the Camunda 7 adapter's {@code database-schema-update} is off. Booting is therefore the
 * proof, and this test says which tables the migration was supposed to bring.
 * </p>
 *
 * <p>
 * Which tables VanillaBP needs is not typed out twice. The test has Liquibase read the changelog
 * of {@code io.vanillabp:vanillabp-schema} and compares what it describes with the names below,
 * so a VanillaBP release which adds a table ends up in this blueprint instead of passing it by.
 * That happened once: the payload table of the phase-two outbox travelled in the artifact for
 * months and was named in no test and in no document here.
 * </p>
 *
 * <p>
 * Who owns what is part of the assertion. One history holds them all, and every changeset is
 * recorded under the logical path of the changelog which declared it, so the rows of the
 * workflow module stay the module's however the application included them. Were that path
 * missing, a module's changesets would be recorded under the application's file name and a
 * later version of the module could no longer recognize its own history.
 * </p>
 */
@SpringBootTest
public class SchemaIT {

  /** The changelog of {@code io.vanillabp:vanillabp-schema}, which db/changelog.xml includes. */
  private static final String SCHEMA_ARTIFACT_CHANGELOG = "vanillabp/schema/changelog.xml";

  /**
   * The tables VanillaBP brings, and the only place in the code of this blueprint which names
   * them. {@code README.md} and {@code AGENTS.md} name them as well, which is why the set is
   * compared against the artifact rather than trusted: the day VanillaBP adds a table,
   * {@link #theSchemaArtifactDescribesTheTablesThisBlueprintKnows()} fails and whoever takes the
   * new name over writes it into both documents too.
   */
  private static final Set<String> TABLES_OF_VANILLABP = Set.of(
      "VANILLABP_PHASE_TWO_OUTBOX",
      "VANILLABP_PHASE_TWO_OUTBOX_PAYLOAD",
      "VANILLABP_TASK_DELIVERY");

  @Autowired
  private DataSource dataSource;

  @Test
  public void theSchemaArtifactDescribesTheTablesThisBlueprintKnows() throws Exception {

    assertThat(tablesOfTheSchemaArtifact())
        .describedAs(
            "The changelog of 'io.vanillabp:vanillabp-schema' describes these tables and no"
                + " others. A difference means a VanillaBP release changed them: take the new"
                + " name into TABLES_OF_VANILLABP, into README.md and into AGENTS.md, so this"
                + " blueprint keeps saying what an application has to migrate.")
        .containsExactlyInAnyOrderElementsOf(TABLES_OF_VANILLABP);

  }

  @Test
  public void everyTableOfTheSchemaArtifactWasMigrated() throws Exception {

    assertThat(tablesOfTheDatabase())
        .describedAs(
            "Every table the artifact describes is in the database. A missing one means the"
                + " changelog of the artifact is not included any more, or that the application"
                + " renamed a table with a property of its own.")
        .containsAll(tablesOfTheSchemaArtifact());

  }

  @Test
  public void everyTableCameFromLiquibase() throws Exception {

    final var tables = tablesOfTheDatabase();

    assertThat(tables)
        .describedAs("The workflow module's own table comes from its own changelog")
        .contains("LOAN_APPROVAL");

    assertThat(tables)
        .describedAs("Liquibase keeps its own bookkeeping")
        .contains("DATABASECHANGELOG");

  }

  @Test
  public void everyOwnerIsRecognizableInTheOneHistory() throws Exception {

    assertThat(ownersInTheChangelogHistory())
        .describedAs(
            "One history holds the rows of every owner, each under the logical path its"
                + " changelog declares. That is what keeps a workflow module's changesets the"
                + " module's, whichever changelog included them, and it is why no bookkeeping"
                + " table of its own is needed.")
        .contains("vanillabp/schema", "loan-approval");

  }

  @Test
  public void theEngineTablesCameFromTheEnginesOwnChangelog() throws Exception {

    if (!engineIsEmbedded()) {
      // a remote engine keeps its tables to itself, there is nothing to create here
      return;
    }

    assertThat(tablesOfTheDatabase())
        .describedAs(
            "The embedded engine's tables come from the changelog Camunda ships in its"
                + " engine JAR, included by db/changelog-camunda7.xml")
        .contains("ACT_RU_EXECUTION", "ACT_RE_PROCDEF", "ACT_GE_SCHEMA_LOG");

  }

  /**
   * Asks the artifact which tables it brings instead of repeating a list nobody compares.
   * Liquibase parses the changelog, follows the include of every version and fills its properties
   * in, so the names come out as the artifact spells them. An application which renames a table
   * does so in its own changelog, before the include, and then compares against the name it
   * chose.
   *
   * @return The names of the tables, upper case
   * @throws Exception If the changelog cannot be read.
   */
  private Set<String> tablesOfTheSchemaArtifact() throws Exception {

    try (var classpath = new ClassLoaderResourceAccessor()) {
      return ChangeLogParserFactory
          .getInstance()
          .getParser(SCHEMA_ARTIFACT_CHANGELOG, classpath)
          .parse(SCHEMA_ARTIFACT_CHANGELOG, new ChangeLogParameters(), classpath)
          .getChangeSets()
          .stream()
          .flatMap(changeSet -> changeSet.getChanges().stream())
          .filter(CreateTableChange.class::isInstance)
          .map(change -> ((CreateTableChange) change).getTableName().toUpperCase())
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }

  }

  /**
   * @return The logical paths the applied changesets were recorded under, which is who owns
   *         them.
   * @throws Exception If the history cannot be read.
   */
  private Set<String> ownersInTheChangelogHistory() throws Exception {

    final var owners = new LinkedHashSet<String>();
    try (var connection = dataSource.getConnection(); var statement = connection
        .createStatement(); var resultSet = statement.executeQuery("SELECT FILENAME FROM DATABASECHANGELOG")) {
      while (resultSet.next()) {
        owners.add(resultSet.getString(1));
      }
    }
    return owners;

  }

  /**
   * @return Whether the engine runs inside this application, which is what makes its tables
   *         part of this schema.
   */
  private static boolean engineIsEmbedded() {

    try {
      Class.forName("org.camunda.bpm.engine.ProcessEngine");
      return true;
    } catch (final ClassNotFoundException e) {
      return false;
    }

  }

  /**
   * @return The names of all tables of the database, upper case.
   * @throws Exception If the metadata cannot be read.
   */
  private Set<String> tablesOfTheDatabase() throws Exception {

    final var tables = new LinkedHashSet<String>();
    try (var connection = dataSource.getConnection()) {
      final DatabaseMetaData metaData = connection.getMetaData();
      try (var resultSet = metaData.getTables(null, null, "%", new String[]{
          "TABLE"
      })) {
        while (resultSet.next()) {
          tables.add(
              resultSet
                  .getString("TABLE_NAME")
                  .toUpperCase());
        }
      }
    }
    return tables;

  }

}
