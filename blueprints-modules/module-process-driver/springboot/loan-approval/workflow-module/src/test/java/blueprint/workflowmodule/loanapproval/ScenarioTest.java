package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import blueprint.testdriver.loanapproval.ProcessDriver;
import blueprint.testdriver.loanapproval.gui.LoanApprovalApi;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * Base class of the tests of this workflow module. It runs the module with its real adapters,
 * and every system around it is the simulator: the credit rating, the e-signature service and
 * the Business Cockpit. Nothing is mocked.
 *
 * <p>
 * The simulator plays the environment, and the {@link ProcessDriver} plays the users. A test
 * brings a loan approval to a state with the driver. It does not know the steps on the way. Then
 * it acts like the people and systems around the application: through the API of the user
 * interface, with the client generated from {@code openapi/loan-approval.yaml}, and through the
 * test API of the simulator. At the end it checks the workflow aggregate and what the simulated
 * systems were told.
 * </p>
 *
 * <p>
 * All test classes share this configuration, so Spring starts the application once for all of
 * them.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
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
    properties.add("credit-rating.base-url", () -> simulator
        + "/credit-rating");
    properties.add("e-signature.base-url", () -> simulator
        + "/e-signature");
    properties.add("e-signature.callback-url", () -> CALLBACK_URL);
    properties.add("vanillabp.cockpit.rest.base-url", () -> simulator);
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

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the application under test", e);
    }

  }

}
