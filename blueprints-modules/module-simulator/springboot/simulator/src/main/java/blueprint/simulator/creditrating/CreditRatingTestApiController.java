package blueprint.simulator.creditrating;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The test API of the simulated credit rating. It prepares what the credit rating knows, and
 * knows nothing about loans or workflows.
 *
 * <p>
 * Every action is a GET, so a person can use it in a browser as well, like the API of the
 * application. A test uses the same URLs.
 * </p>
 */
@RestController
@RequestMapping("/simulator/test-api/credit-rating")
public class CreditRatingTestApiController {

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
  @GetMapping("/next-id")
  public String nextId() {

    return scores.nextCustomerId();

  }

  /**
   * Sets the score of a customer. The caller chooses the id, either a reserved one or one of
   * its own.
   *
   * @param customerId The id of the customer.
   * @param score      The score the credit rating answers from now on.
   * @return The customer id.
   */
  @GetMapping("/create-rating")
  public String createRating(
      @RequestParam final String customerId,
      @RequestParam final int score) {

    scores.set(customerId, score);
    return customerId;

  }

}
