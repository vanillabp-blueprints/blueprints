package blueprint.externalsystems.creditrating.rest;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import blueprint.externalsystems.creditrating.CreditScore;

/**
 * The credit rating, reached over REST. The interface it speaks is described in
 * {@code external-systems/credit-rating/openapi.yaml} of this blueprint.
 *
 * <p>
 * This class is the only place which knows the API of the credit rating: its paths, its JSON
 * and its status codes. It translates all of it into the types of the port. So the workflow
 * module reads {@code creditRating.scoreOf(customerId)} and never sees HTTP.
 * </p>
 *
 * <p>
 * The same class runs against the simulator and against the real system. Only
 * {@code credit-rating.base-url} differs.
 * </p>
 */
public class CreditRatingRestAdapter implements CreditRating {

  /** The answer of the credit rating, in its own words. Only this class sees it. */
  record ScoreResponse(
                       String customerId,
                       int score) {
  }

  private final RestClient creditRating;

  private final String baseUrl;

  /**
   * Creates the adapter.
   *
   * @param creditRating A REST client whose base address is the credit rating.
   * @param baseUrl      The same address, for the messages of this class.
   */
  public CreditRatingRestAdapter(
      final RestClient creditRating,
      final String baseUrl) {

    this.creditRating = creditRating;
    this.baseUrl = baseUrl;

  }

  @Override
  public Optional<CreditScore> scoreOf(
      final String customerId) {

    try {

      return creditRating
          .get()
          .uri("/api/v1/customers/{customerId}/score", customerId)
          .exchange((
              request,
              response) -> {
            if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
              return Optional.empty();
            }
            if (response.getStatusCode().isError()) {
              throw new CreditRatingUnavailableException("The credit rating at '"
                  + baseUrl
                  + "' answered "
                  + response.getStatusCode().value(), null);
            }
            return Optional.of(new CreditScore(response.bodyTo(ScoreResponse.class).score()));
          });

    } catch (ResourceAccessException e) {

      // Nothing answers at the address, or the answer took longer than the timeout.
      throw new CreditRatingUnavailableException("The credit rating at '"
          + baseUrl
          + "' does not answer", e);

    }

  }

}
