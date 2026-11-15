package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.SimulatorTestApi.simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Points every adapter at the simulator, before the application under test starts. The
 * application does the same in its profile 'local', with the port a simulator started by hand
 * uses.
 *
 * <p>
 * The port of the application under test is chosen here as well, before the application starts,
 * because the e-signature service has to know where to call back.
 * </p>
 */
public class EverySystemIsTheSimulator implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {

    final var port = freePort();
    final var simulator = simulator().baseUrl();

    return Map.of(
        "quarkus.http.test-port", Integer.toString(port),
        "credit-rating.base-url", simulator
            + "/credit-rating",
        "e-signature.base-url", simulator
            + "/e-signature",
        "e-signature.callback-url", "http://localhost:"
            + port
            + "/e-signature/callback",
        "vanillabp.cockpit.rest.base-url", simulator);

  }

  @Override
  public void stop() {

    // Nothing was started. The simulator belongs to the build, or to whoever started it.

  }

  private static int freePort() {

    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port for the application under test", e);
    }

  }

}
