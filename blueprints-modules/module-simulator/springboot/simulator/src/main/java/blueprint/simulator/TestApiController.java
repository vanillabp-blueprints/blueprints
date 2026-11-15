package blueprint.simulator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import blueprint.simulator.testapi.server.StatusApi;

/**
 * The part of the test API which belongs to no simulated system.
 *
 * <p>
 * The paths and the parameters come from {@link StatusApi}, the interface generated from
 * {@code simulator-test-api/simulator-test-api.yaml}. This class only implements it.
 * </p>
 */
@RestController
public class TestApiController implements StatusApi {

  /**
   * Says that the simulator runs. A test asks this before it starts, and skips when nobody
   * answers.
   *
   * @return Always {@code running}.
   */
  @Override
  public ResponseEntity<String> status() {

    return ResponseEntity.ok("running");

  }

}
