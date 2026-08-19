package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Who owns what is part of the assertion, and with Flyway that is a table rather than an
 * attribute: one migration history per owner. Flyway keeps one timeline per history table and has
 * no notion of who wrote a migration, so a shared history would have VanillaBP and the application
 * competing for version numbers.
 * </p>
 */
@SpringBootTest
public class SchemaIT {

  @Autowired
  private DataSource dataSource;

  @Test
  public void everyTableCameFromLiquibase() throws Exception {

    final var tables = tablesOfTheDatabase();

    assertThat(tables)
        .describedAs("The tables VanillaBP needs come from the SQL in 'vanillabp-schema'")
        .contains("VANILLABP_PHASE_TWO_OUTBOX", "VANILLABP_TASK_DELIVERY");

    assertThat(tables)
        .describedAs("The outbox table of the Spring Boot integration comes from db/migration")
        .contains("TXNO_OUTBOX", "TXNO_SEQUENCE");

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
