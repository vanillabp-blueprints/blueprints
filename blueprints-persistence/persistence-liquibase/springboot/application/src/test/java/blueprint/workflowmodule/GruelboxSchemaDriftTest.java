package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import com.gruelbox.transactionoutbox.DefaultPersistor;
import com.gruelbox.transactionoutbox.Dialect;
import com.gruelbox.transactionoutbox.Instantiator;
import com.gruelbox.transactionoutbox.TransactionManager;
import com.gruelbox.transactionoutbox.TransactionOutbox;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * The guard on the one piece of somebody else's schema this application carries.
 *
 * <p>
 * {@code db/gruelbox-outbox.xml} describes the outbox table of gruelbox, because switching
 * VanillaBP's table creation off switches gruelbox's migrator off with it. Those statements
 * were read out of a database gruelbox had migrated, and they are only right until gruelbox
 * changes its schema. This test lets gruelbox migrate an empty database again and compares:
 * a version which adds or renames a column fails the build instead of a deployment.
 * </p>
 *
 * <p>
 * Compared are table and column names, not types. The types are gruelbox's business and
 * differ per database anyway; a column which appears, disappears or is renamed is what
 * would actually break, and that is what names show.
 * </p>
 *
 * <p>
 * {@code TXNO_VERSION} is left out on purpose: it is the bookkeeping of the migrator, and
 * an application which creates the schema itself never runs the migrator.
 * </p>
 */
public class GruelboxSchemaDriftTest {

  private static final String MIGRATOR_BOOKKEEPING = "TXNO_VERSION";

  @Test
  public void theChangelogSaysWhatGruelboxWouldHaveCreated() throws Exception {

    final var byGruelbox = schemaAfter("drift-gruelbox", GruelboxSchemaDriftTest::letGruelboxMigrate);
    final var byChangelog = schemaAfter("drift-changelog", GruelboxSchemaDriftTest::applyTheChangelog);

    assertThat(byChangelog)
        .describedAs(
            "db/gruelbox-outbox.xml has to describe what gruelbox's own migrator creates."
                + " If this fails after a gruelbox upgrade, read the schema out of a"
                + " migrated database again and correct the changelog with a NEW changeset.")
        .isEqualTo(byGruelbox);

  }

  /**
   * @param database The name of the in-memory database to build
   * @param schemaCreation What creates the schema in it
   * @return Table name to column names, of gruelbox's tables only
   * @throws Exception If the schema cannot be created or read
   */
  private static Map<String, Set<String>> schemaAfter(
      final String database,
      final SchemaCreation schemaCreation) throws Exception {

    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:"
        + database
        + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    schemaCreation.createIn(dataSource);

    final var schema = new LinkedHashMap<String, Set<String>>();
    try (var connection = dataSource.getConnection()) {
      final var metaData = connection.getMetaData();
      try (var tables = metaData.getTables(null, null, "TXNO%", new String[]{
          "TABLE"
      })) {
        while (tables.next()) {
          final var table = tables
              .getString("TABLE_NAME")
              .toUpperCase();
          if (MIGRATOR_BOOKKEEPING.equals(table)) {
            continue;
          }
          schema.put(table, columnsOf(connection, table));
        }
      }
    }
    return schema;

  }

  private static Set<String> columnsOf(
      final Connection connection,
      final String table) throws Exception {

    final var columns = new LinkedHashSet<String>();
    try (var resultSet = connection
        .getMetaData()
        .getColumns(null, null, table, "%")) {
      while (resultSet.next()) {
        columns.add(
            resultSet
                .getString("COLUMN_NAME")
                .toUpperCase());
      }
    }
    return columns;

  }

  private static void letGruelboxMigrate(
      final DataSource dataSource) {

    // building the outbox runs gruelbox's migrator, which is all this needs
    TransactionOutbox
        .builder()
        .transactionManager(TransactionManager.fromDataSource(dataSource))
        .instantiator(Instantiator.usingReflection())
        .persistor(
            DefaultPersistor
                .builder()
                .dialect(Dialect.H2)
                .migrate(true)
                .build())
        .build();

  }

  private static void applyTheChangelog(
      final DataSource dataSource) throws Exception {

    try (var connection = dataSource.getConnection()) {
      final var database = DatabaseFactory
          .getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (var liquibase = new Liquibase(
          "db/gruelbox-outbox.xml", new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(new Contexts(), new LabelExpression());
      }
    }

  }

  /** What fills an empty database, either of the two ways under comparison. */
  private interface SchemaCreation {

    void createIn(
        DataSource dataSource) throws Exception;

  }

}
