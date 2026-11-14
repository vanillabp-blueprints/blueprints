package blueprint.externalsystems.creditrating.rest;

import java.util.Optional;

import blueprint.externalsystems.creditrating.CreditRating;
import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import blueprint.externalsystems.creditrating.CreditScore;
import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

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

  /** The status the API answers for a customer it does not know. */
  private static final int NOT_FOUND = 404;

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
          .getScore()));

    } catch (WebApplicationException e) {

      // The API answers 404 for a customer it does not know. That is an answer, not a failure.
      if (e.getResponse().getStatus() == NOT_FOUND) {
        return Optional.empty();
      }
      throw new CreditRatingUnavailableException("The credit rating at '"
          + baseUrl
          + "' answered "
          + e.getResponse().getStatus(), e);

    } catch (ProcessingException e) {

      // Nothing answers at the address, or the answer took longer than the timeout.
      throw new CreditRatingUnavailableException("The credit rating at '"
          + baseUrl
          + "' does not answer", e);

    }

  }

}
