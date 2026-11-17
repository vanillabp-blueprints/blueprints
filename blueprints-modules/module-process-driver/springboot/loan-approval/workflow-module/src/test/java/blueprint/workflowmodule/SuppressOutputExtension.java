package blueprint.workflowmodule;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps the build log of a green test class empty, and shows everything a test printed when it
 * fails.
 *
 * <p>
 * A test which makes a surrounding system fail on purpose makes the application log that
 * failure, often with the stack trace of the engine. That is right: in production somebody needs
 * these lines. In the log of a green build they only hide the lines which matter. So this
 * extension does not drop them. It collects them, and prints them only for a test which fails.
 * A log level would drop them for good, and a failing test would then show nothing.
 * </p>
 *
 * <p>
 * It collects what goes to {@code System.out} and {@code System.err}. The console appender of
 * Logback, which Spring Boot uses, writes to whatever {@code System.out} is at the time of the
 * write, so the log of the application is collected as well.
 * </p>
 *
 * <p>
 * What is printed after {@code beforeAll} and before the first test, mostly the start of the
 * application, is shown in front of every failing test of the class. The reason for a red test
 * is often there.
 * </p>
 *
 * <p>
 * Register it before every other extension of the class which prints, so that they print into
 * the collected output: JUnit calls {@code afterEach} in reverse order, and this extension
 * decides in its {@code afterEach} whether to show what it collected.
 * </p>
 *
 * <p>
 * Part of the test harness which every blueprint carries in the same form. In a project made
 * from a blueprint it is a file of that project, so change it as the project needs.
 * </p>
 */
public class SuppressOutputExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

  private PrintStream console;

  private PrintStream consoleForErrors;

  private ByteArrayOutputStream printedBeforeTheFirstTest;

  private ByteArrayOutputStream printedByTheClass;

  private ByteArrayOutputStream printedNow;

  @Override
  public void beforeAll(
      final ExtensionContext context) {

    console = System.out;
    consoleForErrors = System.err;
    printedByTheClass = new ByteArrayOutputStream();
    printedBeforeTheFirstTest = null;
    collect();

  }

  @Override
  public void beforeEach(
      final ExtensionContext context) {

    keepWhatWasPrinted();
    if (printedBeforeTheFirstTest == null) {
      printedBeforeTheFirstTest = printedNow;
    }
    collect();

  }

  @Override
  public void afterEach(
      final ExtensionContext context) {

    keepWhatWasPrinted();
    if (context
        .getExecutionException()
        .isPresent()) {
      show("Output of '"
          + context.getDisplayName()
          + "'", printedBeforeTheFirstTest, printedNow);
    }
    collect();

  }

  @Override
  public void afterAll(
      final ExtensionContext context) {

    keepWhatWasPrinted();
    System.setOut(console);
    System.setErr(consoleForErrors);
    if (context
        .getExecutionException()
        .isPresent()) {
      // Something went wrong outside of a single test, for example the application did not
      // start. Everything the class printed may hold the reason.
      show("Output of the test class", printedByTheClass);
    }

  }

  /** Starts to collect what is printed from now on. */
  private void collect() {

    printedNow = new ByteArrayOutputStream();
    final var stream = new PrintStream(printedNow, true, StandardCharsets.UTF_8);
    System.setOut(stream);
    System.setErr(stream);

  }

  /** Adds what was collected since the last call of {@link #collect()} to the output of the class. */
  private void keepWhatWasPrinted() {

    System.out.flush();
    printedByTheClass.writeBytes(printedNow.toByteArray());

  }

  private void show(
      final String title,
      final ByteArrayOutputStream... outputs) {

    console.println("----- "
        + title
        + " -----");
    for (final var output : outputs) {
      if (output != null) {
        console.print(output.toString(StandardCharsets.UTF_8));
      }
    }
    console.println("----- End of the output -----");

  }

}
