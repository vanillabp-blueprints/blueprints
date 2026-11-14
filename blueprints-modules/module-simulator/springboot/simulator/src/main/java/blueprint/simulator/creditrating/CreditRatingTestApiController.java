package blueprint.simulator.creditrating;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import blueprint.simulator.testapi.server.CreditRatingTestApi;

/**
 * The test API of the simulated credit rating. It prepares what the credit rating knows, and
 * knows nothing about loans or workflows.
 *
 * <p>
 * Every action is a GET, so a person can use it in a browser as well, like the API of the
 * application. A test uses the same URLs.
 * </p>
 *
 * <p>
 * The paths and the parameters come from {@link CreditRatingTestApi}, the interface generated from
 * {@code simulator-test-api/simulator-test-api.yaml}. This class only implements it.
 * </p>
 */
@RestController
public class CreditRatingTestApiController implements CreditRatingTestApi {

  private final CreditScores scores;

  /**
   * Creates the controller.
   *
   * @param scores What the simulated credit rating knows.
   */
  public CreditRatingTestApiController(
      final CreditScores scores) {

    this.scores = scores;

  }

  /**
   * Reserves a customer id nobody used before. A test which takes its customer from here does
   * not collide with a test which runs at the same time.
   *
   * @return The reserved customer id.
   */
  @Override
  public ResponseEntity<String> nextId() {

    return ResponseEntity.ok(scores.nextCustomerId());

  }

  /**
   * Sets the score of a customer. The caller chooses the id, either a reserved one or one of
   * its own.
   *
   * @param customerId The id of the customer.
   * @param score      The score the credit rating answers from now on.
   * @return The customer id.
   */
  @Override
  public ResponseEntity<String> createRating(
      final String customerId,
      final Integer score) {

    scores.set(customerId, score);
    return ResponseEntity.ok(customerId);

  }

}
