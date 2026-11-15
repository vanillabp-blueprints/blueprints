package blueprint.simulator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The part of the test API which belongs to no simulated system.
 */
@RestController
public class TestApiController {

  /**
   * Says that the simulator runs. A test asks this before it starts, and skips when nobody
   * answers.
   *
   * @return Always {@code running}.
   */
  @GetMapping("/simulator/test-api/status")
  public String status() {

    return "running";

  }

}
