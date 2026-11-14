package blueprint.externalsystems.creditrating;

import java.util.Optional;

/**
 * The credit rating, as the application sees it: it tells how likely a customer pays back.
 *
 * <p>
 * The interface is named after the role the system plays, not after the product which plays it
 * today. The workflow module knows this interface and nothing else. Which system answers, and
 * how the application talks to it, is decided by the adapter on the classpath.
 * </p>
 */
public interface CreditRating {

  /**
   * Asks for the credit score of a customer. The answer comes at once.
   *
   * @param customerId The id of the customer.
   * @return The score, or empty if the credit rating does not know the customer.
   * @throws CreditRatingUnavailableException If the credit rating does not answer.
   */
  Optional<CreditScore> scoreOf(
      String customerId);

}
