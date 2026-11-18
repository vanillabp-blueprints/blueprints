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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.awaitility.core.ConditionTimeoutException;

import io.vanillabp.cockpit.gui.api.v1.client.OfficialWorkflowlistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTaskRetrieveMode;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTasksRequest;
import io.vanillabp.cockpit.gui.api.v1.client.model.Workflow;
import io.vanillabp.cockpit.gui.api.v1.client.model.WorkflowRetrieveMode;
import io.vanillabp.cockpit.gui.api.v1.client.model.WorkflowsRequest;

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
 *
 * <p>
 * The simulator serves the official GUI API of the cockpit below
 * {@code /official-api/gui/api/v1}. The test reads it with the client generated from the
 * OpenAPI file of that API. The support library of the cockpit builds the client from the key
 * {@code official-gui-api.base-url}, which the test sets to {@link #cockpitBaseUrl()}, and the
 * test hands it to the methods which read.
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

  private CockpitSimulator(
      final String baseUrl) {

    this.baseUrl = baseUrl;

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
   * The address of the cockpit the simulator plays, which is the {@code base-url} of the clients
   * of the official GUI API. The simulator answers each system it plays below a prefix of its
   * own, the cockpit below {@code /official-api}, and the clients add the path
   * {@code /gui/api/v1} of the API themselves.
   *
   * @return The address of the cockpit, without the path of the API.
   */
  public String cockpitBaseUrl() {

    return baseUrl
        + "/official-api";

  }

  /**
   * Waits until the cockpit knows the case of a business id and the condition holds. The cockpit
   * adapter reports through the outbox, so a report arrives a moment after the event which caused
   * it.
   *
   * @param workflowlist The client of the official GUI API of the simulator.
   * @param businessId   The business id of the case, which is the id of the workflow aggregate.
   * @param condition    What the cockpit is expected to know about the case.
   * @return The case, as the official API of the cockpit returns it.
   */
  public Workflow awaitWorkflow(
      final OfficialWorkflowlistApi workflowlist,
      final String businessId,
      final Predicate<Workflow> condition) {

    return awaitReport(
        "case '"
            + businessId
            + "'",
        () -> workflowOf(workflowlist, businessId),
        condition);

  }

  /**
   * Waits until the cockpit knows the user tasks of a case and the condition holds. This is what
   * the case page gets from {@code workflow.getUserTasks(...)}: the cockpit answers it with
   * {@code POST /workflow/{workflowId}/usertasks}.
   *
   * @param workflowlist The client of the official GUI API of the simulator.
   * @param workflowId   The id of the case in the cockpit, not its business id.
   * @param condition    What the cockpit is expected to know about the user tasks.
   * @return The user tasks of the case, open and ended ones.
   */
  public List<UserTask> awaitUserTasksOfWorkflow(
      final OfficialWorkflowlistApi workflowlist,
      final String workflowId,
      final Predicate<List<UserTask>> condition) {

    return awaitReport(
        "user tasks of case '"
            + workflowId
            + "'",
        () -> Optional.ofNullable(workflowlist
            .getUserTasksOfWorkflow(workflowId, false, new UserTasksRequest()
                .mode(UserTaskRetrieveMode.ALL)
                .pageNumber(0)
                .pageSize(100))),
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
          "The cockpit did not get the expected report of the "
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
   * Reads the case of a business id the way a cockpit user interface reads the case list,
   * filtered by the business id.
   *
   * @param workflowlist The client of the official GUI API of the simulator.
   * @param businessId   The business id of the case.
   * @return The case, if the cockpit knows it.
   */
  private static Optional<Workflow> workflowOf(
      final OfficialWorkflowlistApi workflowlist,
      final String businessId) {

    final var page = workflowlist
        .getWorkflows(new WorkflowsRequest()
            .mode(WorkflowRetrieveMode.ALL)
            .businessIds(List.of(businessId))
            .pageNumber(0)
            .pageSize(10), null, null);
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
