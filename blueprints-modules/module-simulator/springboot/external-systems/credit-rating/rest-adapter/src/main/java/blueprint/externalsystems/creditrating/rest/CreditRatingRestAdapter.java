package blueprint.externalsystems.creditrating.rest;

import java.util.Optional;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import blueprint.externalsystems.creditrating.CreditScore;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;

/**
 * The credit rating, reached over REST. The API is described in
 * {@code external-systems/credit-rating/credit-rating-api/credit-rating.yaml} of this blueprint,
 * and {@link CreditRatingApi} is the client generated from it.
 *
 * <p>
 * This class translates between the generated client and the port. It turns the score of the
 * credit rating into a {@link CreditScore}, and the status codes into what the port promises.
 * So the workflow module reads {@code creditRating.scoreOf(customerId)} and never sees HTTP.
 * </p>
 *
 * <p>
 * The same class runs against the simulator and against the real system. Only
 * {@code credit-rating.base-url} differs.
 * </p>
 */
public class CreditRatingRestAdapter implements CreditRating {

  private final CreditRatingApi creditRating;

  private final String baseUrl;

  /**
   * Creates the adapter.
   *
   * @param creditRating The generated client, whose base address is the credit rating.
   * @param baseUrl      The same address, for the messages of this class.
   */
  public CreditRatingRestAdapter(
      final CreditRatingApi creditRating,
      final String baseUrl) {

    this.creditRating = creditRating;
    this.baseUrl = baseUrl;

  }

  @Override
  public Optional<CreditScore> scoreOf(
      final String customerId) {

    try {

      return Optional.of(new CreditScore(creditRating
          .getScore(customerId)
          .score()));

    } catch (HttpClientErrorException.NotFound e) {

      // The API answers 404 for a customer it does not know. That is an answer, not a failure.
      return Optional.empty();

    } catch (RestClientResponseException e) {

      throw new CreditRatingUnavailableException("The credit rating at '"
          + baseUrl
          + "' answered "
          + e.getStatusCode().value(), e);

    } catch (ResourceAccessException e) {

      // Nothing answers at the address, or the answer took longer than the timeout.
      throw new CreditRatingUnavailableException("The credit rating at '"
          + baseUrl
          + "' does not answer", e);

    }

  }

}
