package blueprint.workflowmodule.loanapproval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;

/**
 * How many SQL statements the database of this application has been asked so far.
 *
 * <p>
 * This class is not part of the use case. It is here so the point of the blueprint can be
 * seen rather than believed: ask for the number, let the workflow wait, ask again. An
 * application which has seen enough deletes this class, the endpoint calling it and the
 * trace the JDBC URL asks for.
 * </p>
 *
 * <p>
 * H2 writes down every statement it executes when the JDBC URL says
 * {@code TRACE_LEVEL_FILE=2}, one line per statement next to the database file, and this
 * counts those lines. Reading a file costs the database nothing, so asking does not disturb
 * what it measures. Every database can do something of this kind and each of them calls it
 * something else, so this class is the one place a different database would change the
 * blueprint.
 * </p>
 */
@Component
public class DatabaseTraffic {

  /** What H2 puts in front of a statement it wrote down. */
  private static final String STATEMENT = "/*SQL";

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * Counts what the database has been asked since the trace was started. The file is added
   * to rather than replaced, so the number of one run is the difference between two
   * readings and never the number itself.
   *
   * @return The statements written down so far.
   */
  public long statementsSoFar() {

    final var trace = Path.of(properties.getDatabaseTrace());
    if (!Files.isReadable(trace)) {
      return 0;
    }

    try (var lines = new BufferedReader(
        new InputStreamReader(Files.newInputStream(trace), StandardCharsets.UTF_8))) {

      return lines
          .lines()
          .filter(line -> line.startsWith(STATEMENT))
          .count();

    } catch (final IOException e) {

      throw new UncheckedIOException(
          "Could not read '"
              + trace.toAbsolutePath()
              + "', the statements H2 wrote down. It is written where the JDBC URL says"
              + " 'TRACE_LEVEL_FILE=2', and 'loan-approval.database-trace' has to name the"
              + " same file.", e);

    }

  }

}
