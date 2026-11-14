package blueprint.workflowmodule.loanapproval;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Runs a test only where a simulator runs, and shows what the simulator logged when the test
 * fails.
 *
 * <p>
 * Two cases are told apart. Started from the IDE, a test finds no simulator unless somebody
 * started one. Then the test is skipped, and the message says how to start the simulator. A
 * skipped test is not a red one. Started by the build, the simulator is there by contract: the
 * Maven profile {@code start-stop-simulator} started it and set {@code simulator.required}. Then
 * a missing simulator fails the test, because a build which skips its tests looks green and
 * proves nothing.
 * </p>
 *
 * <p>
 * The simulator runs in a process of its own and writes its log to a file. When a test fails,
 * this extension prints the lines the simulator wrote while the test ran. They show what the
 * application sent to the systems around it, which is often where the reason is.
 * </p>
 */
public class SimulatorExtension implements ExecutionCondition, BeforeEachCallback, TestWatcher {

  /** How long the build gives a simulator it started to answer. */
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

  private static final String LOG_POSITION = "simulator-log-position";

  /** Set once the simulator answered, so it is asked only once per test run. */
  private static volatile boolean running;

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(
      final ExtensionContext context) {

    if (running) {
      return ConditionEvaluationResult.enabled("The simulator runs");
    }

    final var simulator = SimulatorTestApi.simulator();
    final var required = Boolean.getBoolean("simulator.required");
    final var giveUpAt = Instant.now().plus(required ? STARTUP_TIMEOUT : Duration.ZERO);

    while (!simulator.isRunning()) {
      if (Instant.now().isAfter(giveUpAt)) {
        if (required) {
          throw new IllegalStateException("The build started a simulator, but nothing answers at "
              + simulator.baseUrl()
              + " after "
              + STARTUP_TIMEOUT
              + ". The end of its log:\n"
              + logSince(0));
        }
        return ConditionEvaluationResult.disabled("No simulator answers at "
            + simulator.baseUrl()
            + ". Start it with 'java -jar simulator/target/simulator-1.0.0-SNAPSHOT-runnable.jar',"
            + " or run 'mvn verify', which starts one for the integration tests.");
      }
      sleep();
    }

    running = true;
    return ConditionEvaluationResult.enabled("The simulator runs at "
        + simulator.baseUrl());

  }

  @Override
  public void beforeEach(
      final ExtensionContext context) {

    context
        .getStore(ExtensionContext.Namespace.GLOBAL)
        .put(LOG_POSITION, logSize());

  }

  @Override
  public void testFailed(
      final ExtensionContext context,
      final Throwable cause) {

    final var position = context
        .getStore(ExtensionContext.Namespace.GLOBAL)
        .getOrDefault(LOG_POSITION, Long.class, 0L);

    System.err.println("\n===== What the simulator logged while '"
        + context.getDisplayName()
        + "' ran ("
        + logFile()
        + ") =====\n"
        + logSince(position)
        + "\n===== End of the simulator log =====\n");

  }

  private static Path logFile() {

    return Path.of(System.getProperty("simulator.log", "target/simulator.log"));

  }

  private static long logSize() {

    try {
      return Files.exists(logFile())
          ? Files.size(logFile())
          : 0;
    } catch (IOException e) {
      return 0;
    }

  }

  private static String logSince(
      final long position) {

    if (!Files.exists(logFile())) {
      return "(no log at '"
          + logFile().toAbsolutePath()
          + "', so this simulator was not started by the build)";
    }

    try (var log = new RandomAccessFile(logFile().toFile(), "r")) {
      log.seek(Math.min(position, log.length()));
      final var bytes = new byte[(int) (log.length() - log.getFilePointer())];
      log.readFully(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "(cannot read '"
          + logFile()
          + "': "
          + e.getMessage()
          + ")";
    }

  }

  private static void sleep() {

    try {
      Thread.sleep(POLL_INTERVAL);
    } catch (InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      throw new IllegalStateException("Interrupted while waiting for the simulator", e);
    }

  }

}
