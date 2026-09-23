package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

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
 * Which tables VanillaBP needs is not typed out twice. The test reads them from the migrations
 * of {@code io.vanillabp:vanillabp-schema} and compares them with the names below, so a
 * VanillaBP release which adds a table ends up in this blueprint instead of passing it by. That
 * happened once: the payload table of the phase-two outbox travelled in the artifact for months
 * and was named in no test and in no document here.
 * </p>
 *
 * <p>
 * Who owns what is part of the assertion, and with Flyway that is a table rather than an
 * attribute: one migration history per owner. Flyway keeps one timeline per history table and has
 * no notion of who wrote a migration, so a shared history would have VanillaBP and the application
 * competing for version numbers.
 * </p>
 */
@SpringBootTest
public class SchemaIT {

  /** Where the migrations of {@code io.vanillabp:vanillabp-schema} lie, one directory per database. */
  private static final String SCHEMA_ARTIFACT_LOCATION = "vanillabp/schema/flyway";

  /**
   * The statements of the artifact are generated from a Liquibase changelog, so a table is
   * created by one statement which starts a line.
   */
  private static final Pattern CREATE_TABLE = Pattern.compile("(?im)^\\s*CREATE TABLE\\s+(\\S+?)\\s*\\(");

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

  @Autowired
  @Qualifier("vanillaBpFlyway")
  private Flyway flyway;

  @Test
  public void theSchemaArtifactDescribesTheTablesThisBlueprintKnows() throws Exception {

    assertThat(tablesOfTheSchemaArtifact())
        .describedAs(
            "The migrations of 'io.vanillabp:vanillabp-schema' create these tables and no"
                + " others. A difference means a VanillaBP release changed them: take the new"
                + " name into TABLES_OF_VANILLABP, into README.md and into AGENTS.md, so this"
                + " blueprint keeps saying what an application has to migrate.")
        .containsExactlyInAnyOrderElementsOf(TABLES_OF_VANILLABP);

  }

  @Test
  public void everyTableOfTheSchemaArtifactWasMigrated() throws Exception {

    assertThat(tablesOfTheDatabase())
        .describedAs(
            "Every table the artifact creates is in the database. A missing one means its"
                + " migration was not applied: check the location VanillaBP's Flyway instance"
                + " reads, 'blueprint.schema.vanillabp-location'.")
        .containsAll(tablesOfTheSchemaArtifact());

  }

  @Test
  public void everyTableCameFromFlyway() throws Exception {

    final var tables = tablesOfTheDatabase();

    assertThat(tables)
        .describedAs("The workflow module's own table comes from its own migrations")
        .contains("LOAN_APPROVAL");

    assertThat(tables)
        .describedAs("One migration history per owner: VanillaBP, the module, the application")
        .contains(
            "FLYWAY_SCHEMA_HISTORY_VANILLABP", "FLYWAY_SCHEMA_HISTORY_LOAN_APPROVAL",
            "FLYWAY_SCHEMA_HISTORY");

  }

  @Test
  public void everyOwnerCountsItsOwnMigrations() throws Exception {

    assertThat(appliedMigrations("flyway_schema_history_vanillabp"))
        .describedAs("VanillaBP's SQL, out of 'io.vanillabp:vanillabp-schema'")
        .isNotEmpty();

    assertThat(appliedMigrations("flyway_schema_history_loan_approval"))
        .describedAs("the migrations the workflow module brought along in its JAR")
        .isNotEmpty();

  }

  @Test
  public void theEngineTablesCameFromTheEnginesOwnScripts() throws Exception {

    if (!engineIsEmbedded()) {
      // a remote engine keeps its tables to itself, there is nothing to create here
      return;
    }

    assertThat(tablesOfTheDatabase())
        .describedAs(
            "The embedded engine's tables come from the scripts Camunda ships in its"
                + " engine JAR, named for Flyway by the build")
        .contains("ACT_RU_EXECUTION", "ACT_RE_PROCDEF", "ACT_GE_SCHEMA_LOG");

  }

  /**
   * Asks the artifact which tables it brings instead of repeating a list nobody compares. Flyway
   * knows every migration it found and in which location it lies, so VanillaBP's are the scripts
   * which are readable under its location, and the statements in them name a table each.
   *
   * @return The names of the tables, upper case
   * @throws Exception If a migration cannot be read.
   */
  private Set<String> tablesOfTheSchemaArtifact() throws Exception {

    final var location = Stream
        .of(flyway.getConfiguration().getLocations())
        .map(Location::getPath)
        .filter(path -> path.startsWith(SCHEMA_ARTIFACT_LOCATION))
        .findFirst();
    assertThat(location)
        .describedAs(
            "Flyway reads the migrations of VanillaBP, which lie below '%s'",
            SCHEMA_ARTIFACT_LOCATION)
        .isPresent();

    final var tables = new LinkedHashSet<String>();
    for (final var migration : flyway.info().all()) {
      final var sql = migrationOfTheSchemaArtifact(location.get(), migration.getScript());
      if (sql == null) {
        // a migration of another owner: it does not lie in the artifact's location
        continue;
      }
      final var createTable = CREATE_TABLE.matcher(sql);
      while (createTable.find()) {
        tables.add(
            createTable
                .group(1)
                .toUpperCase());
      }
    }
    return tables;

  }

  /**
   * @param location The location of the artifact's migrations
   * @param script The name a migration carries in Flyway's report
   * @return Its SQL, or {@code null} where that migration is not one of the artifact's
   * @throws Exception If the migration cannot be read.
   */
  private static String migrationOfTheSchemaArtifact(
      final String location,
      final String script) throws Exception {

    // Flyway names a migration by its file name where a location was configured for it alone,
    // and by its whole path where a platform integration resolved the locations while the
    // application was built. Both spellings mean the same file.
    final var resource = script.startsWith(location)
        ? script
        : location
            + "/"
            + script;
    try (var sql = SchemaIT.class
        .getClassLoader()
        .getResourceAsStream(resource)) {
      return sql == null ? null : new String(sql.readAllBytes(), StandardCharsets.UTF_8);
    }

  }

  /**
   * @return Whether the engine runs inside this application, which is what makes its tables part
   *         of this schema.
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
   * Reads one owner's history. Every name is quoted and lower case on purpose: Flyway creates its
   * history table and its columns quoted, so on a database which folds unquoted names to upper
   * case they are only found that way.
   *
   * @param history The history table of one owner
   * @return The migrations recorded in it, version and description
   * @throws Exception If the history cannot be read.
   */
  private Set<String> appliedMigrations(
      final String history) throws Exception {

    final var applied = new LinkedHashSet<String>();
    try (var connection = dataSource.getConnection(); var statement = connection
        .createStatement(); var resultSet = statement
            .executeQuery("SELECT \"version\", \"description\" FROM \""
                + history
                + "\" WHERE \"success\" = TRUE")) {
      while (resultSet.next()) {
        applied.add(resultSet.getString(1)
            + " "
            + resultSet.getString(2));
      }
    }
    return applied;

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
