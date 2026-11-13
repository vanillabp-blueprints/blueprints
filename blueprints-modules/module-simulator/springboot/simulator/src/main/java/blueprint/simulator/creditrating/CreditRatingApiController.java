package blueprint.simulator.creditrating;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API of the credit rating, as {@code external-systems/credit-rating/openapi.yaml} describes
 * it. The adapter of the application talks to this exactly as it talks to the real system.
 */
@RestController
@RequestMapping("/credit-rating/api/v1")
public class CreditRatingApiController {

  /** What the credit rating answers, in its own words. */
  record Score(
               String customerId,
               int score) {
  }

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
  @GetMapping("/customers/{customerId}/score")
  public ResponseEntity<Score> score(
      @PathVariable final String customerId) {

    return ResponseEntity.of(scores
        .of(customerId)
        .map(score -> new Score(customerId, score)));

  }

}
