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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialWorkflowlistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;
import io.vanillabp.cockpit.gui.api.v1.client.model.Workflow;
import io.vanillabp.cockpit.gui.api.v1.client.model.WorkflowRetrieveMode;
import io.vanillabp.cockpit.gui.api.v1.client.model.WorkflowsRequest;

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
 * The rest the test reads from the official GUI API of the cockpit, which the simulator serves
 * below {@code /official-api/v1}, with the clients generated from the OpenAPI file of that API.
 * Spring builds them from the key {@code spring.http.serviceclient.official-gui-api.base-url},
 * which the test sets to {@link #officialGuiApi()}, and the test hands them to the methods which
 * read. The report has no such file, so a plain RestClient reads it.
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

  /**
   * As long as {@code WorkflowModuleTest.TIMEOUT}. A report follows the engine command it is
   * about, and that command may come one retry of the outbox late.
   */
  private static final Duration TIMEOUT = Duration.ofMinutes(1);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private static CockpitSimulator running;

  private final String baseUrl;

  private final RestClient simulatorApi;

  private CockpitSimulator(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    this.simulatorApi = RestClient.create(baseUrl
        + "/simulator-api");

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
   * The address of the official GUI API of the simulator. A user interface of the cockpit reads
   * what the cockpit knows there.
   *
   * @return The address, with the path of the API.
   */
  public String officialGuiApi() {

    return baseUrl
        + "/official-api/v1";

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
   * @param tasklist   The client of the official GUI API of the simulator.
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the user task.
   * @return The user task, as the official API of the cockpit returns it.
   */
  public UserTask awaitUserTask(
      final OfficialTasklistApi tasklist,
      final String userTaskId,
      final Predicate<UserTask> condition) {

    return awaitReport(
        "user task '"
            + userTaskId
            + "'",
        () -> userTaskOf(tasklist, userTaskId),
        condition);

  }

  /**
   * Waits until the cockpit knows the workflow of a business case and the condition holds.
   *
   * @param workflowlist The client of the official GUI API of the simulator.
   * @param businessId   The id of the workflow aggregate.
   * @param condition    What the cockpit is expected to know about the workflow.
   * @return The workflow, as the official API of the cockpit returns it.
   */
  public Workflow awaitWorkflow(
      final OfficialWorkflowlistApi workflowlist,
      final String businessId,
      final Predicate<Workflow> condition) {

    return awaitReport(
        "workflow '"
            + businessId
            + "'",
        () -> workflowOf(workflowlist, businessId),
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
   * @param tasklist   The client of the official GUI API of the simulator.
   * @param userTaskId The id of the user task.
   * @return The user task, if the cockpit knows it.
   */
  private static Optional<UserTask> userTaskOf(
      final OfficialTasklistApi tasklist,
      final String userTaskId) {

    try {
      return Optional.ofNullable(tasklist.getUserTask(userTaskId, null).getBody());
    } catch (HttpClientErrorException.NotFound e) {
      // Nobody reported the user task yet.
      return Optional.empty();
    }

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
   * @param workflowlist The client of the official GUI API of the simulator.
   * @param businessId   The id of the workflow aggregate.
   * @return The workflow, if the cockpit knows it.
   */
  private static Optional<Workflow> workflowOf(
      final OfficialWorkflowlistApi workflowlist,
      final String businessId) {

    final var page = workflowlist
        .getWorkflows(new WorkflowsRequest()
            .mode(WorkflowRetrieveMode.ALL)
            .businessIds(List.of(businessId))
            .pageNumber(0)
            .pageSize(10), null, null)
        .getBody();
    return (page == null) || (page.getWorkflows() == null)
        ? Optional.empty()
        : page
            .getWorkflows()
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
