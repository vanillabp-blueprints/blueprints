package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * What this blueprint is about: every table exists, and none of them was created by
 * VanillaBP, by Hibernate or by the engine.
 *
 * <p>
 * That nothing created them at runtime is not asserted here but configured:
 * {@code vanillabp.outbox.create-schema} is off, the schema management strategy is
 * {@code validate} and the Camunda 7 adapter's {@code database-schema-update} is off.
 * Booting is therefore the proof, and this test says which tables the migration was supposed
 * to bring.
 * </p>
 *
 * <p>
 * The history is part of the assertion. There is exactly one, because the Flyway extension applies
 * one configuration per datasource, so every owner's migrations share a timeline. Flyway has no
 * notion of who wrote a migration, which is why the version numbers have to stay apart, and this
 * test is where that is checked.
 * </p>
 */
@QuarkusTest
public class SchemaIT {

  @Inject
  DataSource dataSource;

  @Test
  public void everyTableCameFromLiquibase() throws Exception {

    final var tables = tablesOfTheDatabase();

    assertThat(tables)
        .describedAs("The tables VanillaBP needs come from the SQL in 'vanillabp-schema'")
        .contains("VANILLABP_PHASE_TWO_OUTBOX", "VANILLABP_TASK_DELIVERY");

    assertThat(tables)
        .describedAs("The workflow module's own table comes from its own migrations")
        .contains("LOAN_APPROVAL");

    assertThat(tables)
        .describedAs("Flyway keeps its own bookkeeping, and here it is one table for every owner")
        .contains("FLYWAY_SCHEMA_HISTORY");

  }

  @Test
  public void everyOwnerIsInTheOneHistory() throws Exception {

    final var applied = appliedMigrations("flyway_schema_history");

    assertThat(applied)
        .describedAs(
            "One timeline holds the migrations of every owner, and their versions have to stay"
                + " apart: VanillaBP numbers its own in 2.x, the engine uses its version, and what"
                + " the application and its modules pick is up to them. Applied: "
                + applied)
        .anyMatch(migration -> migration.startsWith("2.0.0"))
        .anyMatch(migration -> migration.startsWith("1.0.0"));

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
