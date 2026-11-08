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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.web.client.RestClient;

/**
 * The Business Cockpit of the integration test: the dev shell simulator of the cockpit, run as
 * a process of its own.
 *
 * <p>
 * The simulator takes the reports a workflow module sends, on the same interface the cockpit
 * offers, and serves them back on the interface a cockpit user interface reads. So the test
 * sees what a user of the cockpit would see, and the workflow module runs with its real
 * cockpit adapter. Nothing is mocked.
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

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private static CockpitSimulator running;

  private final String baseUrl;

  private final RestClient officialApi;

  private CockpitSimulator(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    this.officialApi = RestClient.create(baseUrl
        + "/official-api/v1");

  }

  /**
   * The simulator of this test run. The first call starts it.
   *
   * @return The running simulator.
   */
  public static synchronized CockpitSimulator start() {

    if (running == null) {
      running = new CockpitSimulator(launch());
    }
    return running;

  }

  /**
   * The address a workflow module reports to, which is {@code vanillabp.cockpit.rest.base-url}.
   *
   * @return The address of the simulator.
   */
  public String baseUrl() {

    return baseUrl;

  }

  /**
   * Waits until the cockpit knows the user task and the condition holds. The cockpit adapter
   * reports through the outbox, so a report arrives a moment after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the task.
   * @return The user task, as the official API of the cockpit returns it.
   */
  public Map<String, Object> awaitUserTask(
      final String userTaskId,
      final Predicate<Map<String, Object>> condition) {

    final var lastSeen = new AtomicReference<Optional<Map<String, Object>>>(Optional.empty());

    try {

      await()
          .atMost(TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            final var found = userTask(userTaskId);
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The cockpit did not get the expected report of user task '"
              + userTaskId
              + "' within "
              + TIMEOUT
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("nothing - the task was never reported"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  /**
   * Reads a user task the way a cockpit user interface reads it.
   *
   * @param userTaskId The id of the user task.
   * @return The user task, if the cockpit knows it.
   */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> userTask(
      final String userTaskId) {

    return officialApi
        .get()
        .uri("/usertask/{userTaskId}", userTaskId)
        // The simulator answers a task it does not know with an error. That only means the
        // report has not arrived yet.
        .exchange((
            request,
            response) -> response.getStatusCode().is2xxSuccessful()
                ? Optional.ofNullable((Map<String, Object>) response.bodyTo(Map.class))
                : Optional.empty());

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

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the dev shell simulator", e);
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
