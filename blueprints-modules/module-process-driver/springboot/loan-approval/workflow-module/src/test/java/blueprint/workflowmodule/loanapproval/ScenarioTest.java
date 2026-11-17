package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import blueprint.testdriver.loanapproval.ProcessDriver;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.workflowmodule.SuppressOutputExtension;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;

/**
 * Base class of the tests of this workflow module. It runs the module with its real adapters,
 * and every system around it is the simulator: the credit rating, the e-signature service and
 * the Business Cockpit. Nothing is mocked.
 *
 * <p>
 * The simulator plays the environment, and the {@link ProcessDriver} plays the users. A test brings
 * a loan approval to a state with the driver. It does not know the steps on the way. Then it acts
 * like the people and systems around the application: through the API of the user interface, with
 * the client generated from {@code loan-approval/openapi/loan-approval.yaml}, and through the test
 * API of the simulator. At the end it checks the workflow aggregate and what the simulated systems
 * were told.
 * </p>
 *
 * <p>
 * All test classes share this configuration, so Spring starts the application once for all of
 * them.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
// The output of a test is shown only if it fails, together with what the simulator logged
// meanwhile. So the suppression comes first. See SimulatorExtension for why the order matters.
@ExtendWith(SuppressOutputExtension.class)
@ExtendWith(SimulatorExtension.class)
public abstract class ScenarioTest extends WorkflowModuleTest {

  /**
   * How the application answers a report of the e-signature service it took, as the callback
   * API says.
   */
  protected static final int REPORT_TAKEN = HttpStatus.NO_CONTENT.value();

  /** How the application answers a report about a contract it does not know. */
  protected static final int REPORT_OF_AN_UNKNOWN_CONTRACT = HttpStatus.NOT_FOUND.value();

  /**
   * How long a test watches that nothing happens. Four times as long as the simulated customers
   * take who act by themselves.
   */
  protected static final Duration A_WHILE = Duration.ofSeconds(1);

  /**
   * The port of the application under test. It is chosen before the application starts, because
   * the e-signature service has to know where to call back.
   */
  protected static final int PORT = freePort();

  /** Where the application takes the reports of the e-signature service. */
  protected static final String CALLBACK_URL = "http://localhost:"
      + PORT
      + "/e-signature/callback";

  @Autowired
  protected ProcessDriver driver;

  @Autowired
  protected AggregateRepository loanApprovals;

  /**
   * The API of the user interface of the module, as a browser or a form uses it. The process
   * driver brings the client along.
   */
  @Autowired
  protected LoanApprovalApi loanApproval;

  /**
   * The task list of the cockpit the simulator plays, read through the client of the official
   * GUI API of the cockpit.
   */
  @Autowired
  private OfficialTasklistApi tasklist;

  /**
   * Points every adapter and the process driver at the simulator, and the driver at the
   * application, before the application starts. The application does the same in its profile
   * 'local', with the port a simulator started by hand uses.
   *
   * @param properties The properties of the application under test.
   */
  @DynamicPropertySource
  static void everySystemIsTheSimulator(
      final DynamicPropertyRegistry properties) {

    final var simulator = simulator().baseUrl();
    properties.add("server.port", () -> PORT);
    properties.add("credit-rating.client.base-url", () -> simulator
        + "/credit-rating");
    properties.add("e-signature.client.base-url", () -> simulator
        + "/e-signature");
    properties.add("e-signature.callback-url", () -> CALLBACK_URL);
    properties.add("vanillabp.cockpit.rest.base-url", () -> simulator);
    // The client of the cockpit's library, with the keys where Spring Boot looks for them.
    properties.add("spring.http.serviceclient.official-gui-api.base-url", () -> simulator
        + "/official-api/v1");
    properties.add("process-driver.simulator-base-url", () -> simulator);
    properties.add("process-driver.application-base-url", () -> "http://localhost:"
        + PORT);

  }

  /**
   * What the module answers when a user does something it refuses at this point of the process,
   * for example checks a contract the customer has not signed yet. The task stays open, and the
   * user may try again later.
   *
   * @param userAction What the user does.
   * @return The reason the module gives.
   * @throws AssertionError If the module did not refuse.
   */
  protected static String reasonOfTheRefusal(
      final Runnable userAction) {

    try {
      userAction.run();
    } catch (HttpClientErrorException.Conflict refused) {
      return refused.getResponseBodyAsString();
    }
    throw new AssertionError("The module did not refuse what the user did");

  }

  /**
   * Watches for {@link #A_WHILE} that something stays true, for example that no signature
   * arrives from a customer who never signs.
   *
   * @param condition What has to stay true.
   */
  protected static void staysSoForAWhile(
      final Callable<Boolean> condition) {

    await()
        .during(A_WHILE)
        .atMost(A_WHILE.multipliedBy(2))
        .until(condition);

  }

  /**
   * Waits until the cockpit knows a user task and the condition holds. The cockpit adapter
   * reports through the outbox, so a report arrives a moment after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the task.
   * @return The user task, as the official GUI API of the cockpit returns it.
   */
  protected UserTask awaitUserTask(
      final String userTaskId,
      final Predicate<UserTask> condition) {

    return await("the report of user task '"
        + userTaskId
        + "' in the cockpit")
        .atMost(TIMEOUT)
        .pollDelay(Duration.ZERO)
        .until(() -> userTask(userTaskId), found -> found.filter(condition).isPresent())
        .orElseThrow();

  }

  private Optional<UserTask> userTask(
      final String userTaskId) {

    try {
      return Optional.ofNullable(tasklist.getUserTask(userTaskId, null).getBody());
    } catch (HttpClientErrorException.NotFound e) {
      // The report has not arrived yet.
      return Optional.empty();
    }

  }

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the application under test", e);
    }

  }

}
