package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.gruelbox.transactionoutbox.DefaultPersistor;
import com.gruelbox.transactionoutbox.Dialect;

/**
 * The guard on the one piece of somebody else's schema this application carries.
 *
 * <p>
 * The migrations named {@code *__outbox_of_the_outbox_library.sql} create the outbox table of
 * gruelbox, because switching VanillaBP's table creation off switches gruelbox's migrator off
 * with it. Their statements are not hand-written: gruelbox emits them itself, through
 * {@code DefaultPersistor#writeSchema(Writer)}, and this test asks for them again and
 * compares. A version of the library which adds a migration, changes one or renames a column
 * fails the build instead of a deployment.
 * </p>
 *
 * <p>
 * Every migration of that name is read, in the order Flyway would apply them, so the answer
 * to a gruelbox upgrade stays what Flyway demands: a new migration rather than an edit of one
 * already applied.
 * </p>
 *
 * <p>
 * Compared are the statements, not the schema they produce, and no database is started for
 * it. Two statements which differ produce two schemas which may or may not differ, and the
 * cheaper question is the stricter one.
 * </p>
 *
 * <p>
 * H2 is the dialect this blueprint runs on, so it is the dialect asked for here. An
 * application on another database changes both places, and the comparison keeps holding.
 * </p>
 */
public class GruelboxSchemaDriftTest {

  private static final String MIGRATIONS = "classpath*:db/migration/V*__outbox_of_the_outbox_library.sql";

  /** A migration which does nothing on a dialect is a comment and no statement. */
  private static final Pattern COMMENT = Pattern.compile("(?m)^\\s*--.*$");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Test
  public void theMigrationSaysWhatGruelboxWritesItself() throws Exception {

    assertThat(statementsOfTheMigrations())
        .describedAs(
            "The migrations carry the statements of gruelbox's own writeSchema. If this fails"
                + " after an upgrade of transactionoutbox-core, ask writeSchema again and add"
                + " what is new as a NEW migration - an applied one is never edited.")
        .isEqualTo(statementsOfGruelbox());

  }

  @Test
  public void theMigratorsOwnBookkeepingIsNotPartOfIt() {

    // TXNO_VERSION is how the migrator remembers where it got to, and the migrator is off
    // here. gruelbox does not emit it, which is why no migration creates it.
    assertThat(statementsOfGruelbox())
        .describedAs("writeSchema emits the schema of the outbox, not of its migrator")
        .noneMatch(statement -> statement.contains("TXNO_VERSION"));

  }

  /**
   * @return Every statement gruelbox writes for this dialect, in its order
   */
  private static List<String> statementsOfGruelbox() {

    final var writer = new StringWriter();
    DefaultPersistor
        .builder()
        .dialect(Dialect.H2)
        .build()
        .writeSchema(writer);

    return statementsOf(writer.toString());

  }

  /**
   * @return The statements of every gruelbox migration, in the order Flyway applies them
   * @throws Exception If a migration cannot be read
   */
  private static List<String> statementsOfTheMigrations() throws Exception {

    final var resources = new PathMatchingResourcePatternResolver()
        .getResources(MIGRATIONS);
    assertThat(resources)
        .describedAs("the migration creating gruelbox's tables has to be on the classpath")
        .isNotEmpty();

    final var statements = new ArrayList<String>();
    for (final var resource : sortedByVersion(resources)) {
      statements.addAll(statementsOf(contentOf(resource)));
    }
    return statements;

  }

  private static List<Resource> sortedByVersion(
      final Resource[] resources) {

    return List
        .of(resources)
        .stream()
        .sorted(Comparator.comparing(Resource::getFilename))
        .toList();

  }

  private static String contentOf(
      final Resource resource) throws Exception {

    try (var content = resource.getInputStream()) {
      return new String(content.readAllBytes(), StandardCharsets.UTF_8);
    }

  }

  /**
   * @param sql One or more statements, separated by a blank line respectively a semicolon
   * @return One entry per statement, comments removed and whitespace collapsed, which is
   *         what makes two spellings of one statement equal
   */
  private static List<String> statementsOf(
      final String sql) {

    final var withoutComments = COMMENT
        .matcher(sql)
        .replaceAll("");

    final var statements = new ArrayList<String>();
    for (final var part : withoutComments.split(";|\\n\\s*\\n")) {
      final var statement = WHITESPACE
          .matcher(part)
          .replaceAll(" ")
          .trim();
      if (!statement.isEmpty()) {
        statements.add(statement);
      }
    }
    return statements;

  }

}
