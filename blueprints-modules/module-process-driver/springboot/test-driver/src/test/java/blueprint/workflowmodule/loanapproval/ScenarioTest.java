package blueprint.workflowmodule.loanapproval;

import static blueprint.testdriver.simulator.SimulatorTestApi.simulator;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import blueprint.testdriver.loanapproval.ProcessDriver;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * Base class of the tests of this workflow module. It runs the module with its real adapters,
 * and every system around it is the simulator: the credit rating, the e-signature service and
 * the Business Cockpit. Nothing is mocked.
 *
 * <p>
 * A test brings a loan approval to a state with the {@link ProcessDriver}. It does not know the
 * steps on the way. Then it acts like the people and systems around the application, through the
 * API of the module and the test API of the simulator, and checks the workflow aggregate and what
 * the simulated systems were told.
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
   * The port of the application under test. It is chosen before the application starts, because
   * the e-signature service has to know where to call back.
   */
  protected static final int PORT = freePort();

  @Autowired
  protected ProcessDriver driver;

  @Autowired
  protected AggregateRepository loanApprovals;

  /** The API of the module, as a browser uses it. */
  protected final RestClient api = RestClient.create("http://localhost:"
      + PORT
      + "/api/loan-approval");

  /**
   * Points every adapter and the process driver at the simulator, before the application
   * starts. The application does the same in its profile 'local', with the port a simulator
   * started by hand uses.
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
    properties.add("e-signature.callback-url", () -> "http://localhost:"
        + PORT
        + "/e-signature/callback");
    properties.add("vanillabp.cockpit.rest.base-url", () -> simulator);
    properties.add("process-driver.simulator-base-url", () -> simulator);

  }

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the application under test", e);
    }

  }

}
