package blueprint.simulator.creditrating;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import blueprint.externalsystems.creditrating.api.server.CreditRatingApi;
import blueprint.externalsystems.creditrating.api.server.model.Score;

/**
 * The API of the credit rating. The path, the method and the JSON come from
 * {@link CreditRatingApi}, the interface generated from
 * {@code external-systems/credit-rating/credit-rating-api/credit-rating.yaml}. This class only
 * implements it. The adapter of the application talks to this exactly as it talks to the real
 * system.
 */
@RestController
public class CreditRatingApiController implements CreditRatingApi {

  private final CreditScores scores;

  /**
   * Creates the controller.
   *
   * @param scores What the simulated credit rating knows.
   */
  public CreditRatingApiController(
      final CreditScores scores) {

    this.scores = scores;

  }

  /**
   * The credit score of a customer.
   *
   * @param customerId The id of the customer.
   * @return The score, or 404 for a customer the credit rating does not know.
   */
  @Override
  public ResponseEntity<Score> getScore(
      final String customerId) {

    return ResponseEntity.of(scores
        .of(customerId)
        .map(score -> new Score(customerId, score)));

  }

}
