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
import java.util.function.Supplier;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The Business Cockpit of the integration test: the dev shell simulator of the cockpit, run as
 * a process of its own.
 *
 * <p>
 * The simulator takes the reports a workflow module sends, on the same interface the cockpit
 * offers. So the workflow module runs with its real cockpit adapter, and nothing is mocked. The
 * official API of the cockpit does not show the excluded candidates, the admitted users and the
 * kind of notification of a user task, and these are what this blueprint is about. The simulator
 * keeps them anyway, and it shows the last report about a user task, as the module sent it, at
 * {@code GET /simulator-api/usertask/{id}/report}. The test reads the reports from there.
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

  private final RestClient simulatorApi;

  private final RestClient officialApi;

  private CockpitSimulator(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    this.simulatorApi = RestClient.create(baseUrl
        + "/simulator-api");
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
   * Waits for the last report about one user task, and for it to meet the condition. The report
   * is what the module sent when it created, updated or ended the task, with every field. The
   * cockpit adapter reports through the outbox, so a report arrives a moment after the event
   * which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the report is expected to say.
   * @return The report.
   */
  public Map<String, Object> awaitUserTaskReport(
      final String userTaskId,
      final Predicate<Map<String, Object>> condition) {

    return awaitReport(
        "user task '"
            + userTaskId
            + "'",
        () -> lastReportOf(userTaskId),
        condition);

  }

  /**
   * Waits until the cockpit knows a user task and the condition holds. This is the user task as
   * the official API of the cockpit shows it, for example with the reason it ended.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the user task.
   * @return The user task, as the official API of the cockpit returns it.
   */
  public Map<String, Object> awaitUserTask(
      final String userTaskId,
      final Predicate<Map<String, Object>> condition) {

    return awaitReport(
        "user task '"
            + userTaskId
            + "'",
        () -> userTaskOf(userTaskId),
        condition);

  }

  /**
   * Waits until the cockpit knows the workflow of a business case and the condition holds.
   *
   * @param businessId The id of the workflow aggregate.
   * @param condition  What the cockpit is expected to know about the workflow.
   * @return The workflow, as the official API of the cockpit returns it.
   */
  public Map<String, Object> awaitWorkflow(
      final String businessId,
      final Predicate<Map<String, Object>> condition) {

    return awaitReport(
        "workflow '"
            + businessId
            + "'",
        () -> workflowOf(businessId),
        condition);

  }

  private <T> T awaitReport(
      final String what,
      final Supplier<Optional<T>> read,
      final Predicate<T> condition) {

    final var lastSeen = new AtomicReference<Optional<T>>(Optional.empty());

    try {

      await()
          .atMost(TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            final var found = read.get();
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The cockpit did not get the expected report about the "
              + what
              + " within "
              + TIMEOUT
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("nothing - it was never reported"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  /**
   * The last report about a user task, which only the simulator shows.
   *
   * @param userTaskId The id of the user task.
   * @return The report, if the simulator knows the user task.
   */
  private Optional<Map<String, Object>> lastReportOf(
      final String userTaskId) {

    return getIfKnown(simulatorApi, "/usertask/{userTaskId}/report", userTaskId);

  }

  /**
   * Reads a user task the way a cockpit user interface reads it.
   *
   * @param userTaskId The id of the user task.
   * @return The user task, if the cockpit knows it.
   */
  private Optional<Map<String, Object>> userTaskOf(
      final String userTaskId) {

    return getIfKnown(officialApi, "/usertask/{userTaskId}", userTaskId);

  }

  /**
   * Sends a GET request. The simulator answers 404 for a user task nobody reported yet.
   *
   * @param api        The API to ask.
   * @param path       The path below the API.
   * @param userTaskId The id of the user task in the path.
   * @return The answer, or nothing if the simulator does not know the user task yet.
   */
  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> getIfKnown(
      final RestClient api,
      final String path,
      final String userTaskId) {

    return api
        .get()
        .uri(path, userTaskId)
        .exchange((
            request,
            response) -> response.getStatusCode() == HttpStatus.NOT_FOUND
                ? Optional.empty()
                : Optional.of((Map<String, Object>) response.bodyTo(Map.class)));

  }

  /**
   * Reads the workflow of a business id the way a cockpit user interface reads the list of
   * workflows, filtered by the business id.
   *
   * @param businessId The id of the workflow aggregate.
   * @return The workflow, if the cockpit knows it.
   */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> workflowOf(
      final String businessId) {

    final Map<String, Object> page = officialApi
        .post()
        .uri("/workflow")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
            "mode", "All",
            "businessIds", List.of(businessId),
            "pageNumber", 0,
            "pageSize", 10))
        .retrieve()
        .body(Map.class);
    final var workflows = (List<Map<String, Object>>) page.get("workflows");
    return workflows == null
        ? Optional.empty()
        : workflows
            .stream()
            .findFirst();

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
