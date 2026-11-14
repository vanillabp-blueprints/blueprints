package blueprint.workflowmodule.loanapproval;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.awaitility.core.ConditionTimeoutException;

/**
 * The Business Cockpit of the integration test: the dev shell simulator of the cockpit, run as
 * a process of its own, with a {@link ReportRecorder} in front of it.
 *
 * <p>
 * The simulator takes the reports a workflow module sends, on the same interface the cockpit
 * offers. So the workflow module runs with its real cockpit adapter, and nothing is mocked. The
 * simulator keeps less of a report than the cockpit does. It drops the excluded candidates, the
 * admitted users and the kind of notification, and these are what this blueprint is about. So
 * the recorder keeps a copy of every report on its way, and the test reads what the module
 * reported from there.
 * </p>
 *
 * <p>
 * Maven copies the runnable JAR of the simulator to {@code target/} before the integration
 * tests run. This class starts it once per test run, on a free port, and stops it when the test
 * run ends. Its log goes to {@code target/dev-shell-simulator.log}.
 * </p>
 */
public final class CockpitSimulator {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private static ReportRecorder running;

  private CockpitSimulator() {
  }

  /**
   * The cockpit of this test run, which is the recorder in front of the simulator. The first
   * call starts both.
   *
   * @return The recorder, whose address the workflow module reports to.
   */
  public static synchronized ReportRecorder start() {

    if (running == null) {
      running = ReportRecorder.inFrontOf(launch());
    }
    return running;

  }

  private static String launch() {

    final var jar = Path.of(System.getProperty("dev-shell-simulator.jar", "target/dev-shell-simulator.jar"));
    final var log = Path.of(System.getProperty("dev-shell-simulator.log", "target/dev-shell-simulator.log"));
    if (!Files.isRegularFile(jar)) {
      throw new IllegalStateException("The dev shell simulator is missing at '"
          + jar.toAbsolutePath()
          + "'. Maven copies it there before the integration tests run, so start the test"
          + " with 'mvn verify'.");
    }

    final var port = freePort();
    final var java = ProcessHandle
        .current()
        .info()
        .command()
        .orElse("java");

    try {

      final var process = new ProcessBuilder(
          java, "-jar", jar.toString(), "--server.port="
              + port, "--spring.config.additional-location="
                  + usersFile())
          .redirectErrorStream(true)
          .redirectOutput(log.toFile())
          .start();
      // The simulator belongs to this test run and must not outlive it.
      Runtime
          .getRuntime()
          .addShutdownHook(new Thread(process::destroy));

      await()
          .atMost(STARTUP_TIMEOUT)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            if (!process.isAlive()) {
              throw new IllegalStateException("The dev shell simulator ended while it started:\n"
                  + tailOf(log));
            }
            return Files.readString(log, StandardCharsets.UTF_8).contains("Started DevShellSimulator");
          });

    } catch (IOException e) {
      throw new IllegalStateException("Cannot start the dev shell simulator", e);
    } catch (ConditionTimeoutException e) {
      throw new IllegalStateException("The dev shell simulator did not start within "
          + STARTUP_TIMEOUT
          + ":\n"
          + tailOf(log), e);
    }

    return "http://localhost:"
        + port;

  }

  private static String usersFile() {

    try {
      return Path
          .of(CockpitSimulator.class.getResource("/dev-shell-simulator.yaml").toURI())
          .toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }

  }

  /**
   * A port nobody listens on right now.
   *
   * @return The port.
   */
  static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port", e);
    }

  }

  private static String tailOf(
      final Path log) {

    try {
      final List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
      return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    } catch (IOException e) {
      return "(no log at '"
          + log
          + "')";
    }

  }

}
