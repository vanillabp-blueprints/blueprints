package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import com.gruelbox.transactionoutbox.DefaultPersistor;
import com.gruelbox.transactionoutbox.Dialect;

/**
 * The guard on the one piece of somebody else's schema this application carries.
 *
 * <p>
 * {@code db/gruelbox-outbox.xml} creates the outbox table of gruelbox, because switching
 * VanillaBP's table creation off switches gruelbox's migrator off with it. The statements in
 * that file are not hand-written: gruelbox emits them itself, through
 * {@code DefaultPersistor#writeSchema(Writer)}, and this test asks for them again and
 * compares. A version of the library which adds a migration, changes one or renames a column
 * fails the build instead of a deployment.
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

  private static final String CHANGELOG = "db/gruelbox-outbox.xml";

  /** A migration which does nothing on a dialect is a comment and no statement. */
  private static final Pattern COMMENT = Pattern.compile("(?m)^\\s*--.*$");

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Test
  public void theChangelogSaysWhatGruelboxWritesItself() throws Exception {

    assertThat(statementsOfTheChangelog())
        .describedAs(
            "db/gruelbox-outbox.xml carries the statements of gruelbox's own writeSchema. If"
                + " this fails after an upgrade of transactionoutbox-core, ask writeSchema"
                + " again and add what is new as a NEW changeset - the applied ones stay as"
                + " they are.")
        .isEqualTo(statementsOfGruelbox());

  }

  @Test
  public void theMigratorsOwnBookkeepingIsNotPartOfIt() throws Exception {

    // TXNO_VERSION is how the migrator remembers where it got to, and the migrator is off
    // here. gruelbox does not emit it, which is why the changelog does not create it.
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

    final var statements = new ArrayList<String>();
    for (final var block : writer
        .toString()
        .split("\\n\\s*\\n")) {
      final var statement = normalized(block);
      if (!statement.isEmpty()) {
        statements.add(statement);
      }
    }
    return statements;

  }

  /**
   * @return The statement of every changeset of the changelog, in document order
   * @throws Exception If the changelog cannot be read
   */
  private static List<String> statementsOfTheChangelog() throws Exception {

    final var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try (var changelog = GruelboxSchemaDriftTest.class
        .getClassLoader()
        .getResourceAsStream(CHANGELOG)) {
      final var document = factory
          .newDocumentBuilder()
          .parse(changelog);
      final var elements = document.getElementsByTagNameNS("*", "sql");
      final var statements = new ArrayList<String>();
      for (var index = 0; index < elements.getLength(); index++) {
        statements.add(normalized(((Element) elements.item(index)).getTextContent()));
      }
      return statements;
    }

  }

  /**
   * @param sql One statement, as written in a file
   * @return The statement without comments, without a trailing semicolon and with its
   *         whitespace collapsed, which is what makes two spellings of one statement equal
   */
  private static String normalized(
      final String sql) {

    final var withoutComments = COMMENT
        .matcher(sql)
        .replaceAll("");
    final var collapsed = WHITESPACE
        .matcher(withoutComments)
        .replaceAll(" ")
        .trim();
    return collapsed.endsWith(";")
        ? collapsed
            .substring(0, collapsed.length() - 1)
            .trim()
        : collapsed;

  }

}
