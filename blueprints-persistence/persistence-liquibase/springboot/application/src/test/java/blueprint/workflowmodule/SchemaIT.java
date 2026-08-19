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
 * The bookkeeping tables are part of the assertion. Two owners, two histories: the
 * application's default {@code DATABASECHANGELOG} and the workflow module's own. Were the
 * module writing into the application's table, the two could no longer be upgraded
 * independently, and nothing but this assertion would notice.
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
        .describedAs("The tables VanillaBP needs come from 'vanillabp/schema/changelog.xml'")
        .contains("VANILLABP_PHASE_TWO_OUTBOX", "VANILLABP_TASK_DELIVERY");

    assertThat(tables)
        .describedAs("The outbox table of the Spring Boot integration comes from db/gruelbox-outbox.xml")
        .contains("TXNO_OUTBOX", "TXNO_SEQUENCE");

    assertThat(tables)
        .describedAs("The workflow module's own table comes from its own changelog")
        .contains("LOAN_APPROVAL");

    assertThat(tables)
        .describedAs("One bookkeeping table per owner: the application's and the module's")
        .contains("DATABASECHANGELOG", "DATABASECHANGELOG_LOAN_APPROVAL");

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
