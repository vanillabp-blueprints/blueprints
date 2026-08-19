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
 * Who owns what is part of the assertion. One history holds them all, and every changeset is
 * recorded under the logical path of the changelog which declared it, so the rows of the
 * workflow module stay the module's however the application included them. Were that path
 * missing, a module's changesets would be recorded under the application's file name and a
 * later version of the module could no longer recognize its own history.
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
        .describedAs("The tables VanillaBP needs come from 'vanillabp/schema/changelog.xml'")
        .contains("VANILLABP_PHASE_TWO_OUTBOX", "VANILLABP_TASK_DELIVERY");

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
