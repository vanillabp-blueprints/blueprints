package blueprint.workflowmodule;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.LogManager;

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
 * It collects what goes to {@code System.out} and {@code System.err}. That alone does not reach
 * the log of the application: Quarkus logs through JBoss LogManager, and its console handler
 * keeps the {@code System.out} it found when it was made. So this extension gives every stream
 * handler of the log manager a stream which writes to whatever {@code System.out} is at the time
 * of the write. Then the log of the application is collected as well, and it goes back to the
 * console when the extension puts the console back.
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
    // Handlers come and go while Quarkus starts, so this is done every time. Twice does no harm.
    followSystemOut();

  }

  /** Adds what was collected since the last call of {@link #collect()} to the output of the class. */
  private void keepWhatWasPrinted() {

    System.out.flush();
    printedByTheClass.writeBytes(printedNow.toByteArray());

  }

  /**
   * Gives every stream handler of JBoss LogManager a stream which writes to the current
   * {@code System.out}. Quarkus puts its console handler into another handler while the
   * application starts, so nested handlers are visited as well. Without JBoss LogManager nothing
   * happens.
   */
  private static void followSystemOut() {

    final var logManager = LogManager.getLogManager();
    if (!logManager
        .getClass()
        .getName()
        .startsWith("org.jboss.logmanager.")) {
      return;
    }
    final var root = logManager.getLogger("");
    if (root == null) {
      return;
    }
    for (final var handler : root.getHandlers()) {
      followSystemOut(handler, 0);
    }

  }

  private static void followSystemOut(
      final Handler handler,
      final int depth) {

    // Deeper than this is a cycle, and none of a handler this extension could change.
    if ((handler == null) || (depth > 8)) {
      return;
    }
    try {
      final Method setOutputStream = handler
          .getClass()
          .getMethod("setOutputStream", OutputStream.class);
      setOutputStream.invoke(handler, new CurrentSystemOut());
    } catch (NoSuchMethodException e) {
      // Not a stream handler, so it prints nowhere near the build log.
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot point the log handler '"
          + handler
              .getClass()
              .getName()
          + "' at the collected output, so a green test would print into the build log", e);
    }
    try {
      if (handler
          .getClass()
          .getMethod("getHandlers")
          .invoke(handler) instanceof Handler[] nested) {
        for (final var inner : nested) {
          followSystemOut(inner, depth + 1);
        }
      }
    } catch (ReflectiveOperationException e) {
      // A handler which wraps no others.
    }

  }

  /**
   * Writes to whatever {@code System.out} is at the time of the write. Closing it only flushes,
   * because the handler which owns it must not close the console.
   */
  private static class CurrentSystemOut extends OutputStream {

    @Override
    public void write(
        final int b) {

      System.out.write(b);

    }

    @Override
    public void write(
        final byte[] b,
        final int off,
        final int len) {

      System.out.write(b, off, len);

    }

    @Override
    public void flush() {

      System.out.flush();

    }

    @Override
    public void close() {

      System.out.flush();

    }

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
