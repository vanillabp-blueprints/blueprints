package blueprint.externalsystems.creditbureau.rest;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import blueprint.externalsystems.creditbureau.api.client.model.ScoreRequest;
import blueprint.workflowmodule.loanapproval.CreditBureau;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

/**
 * The credit bureau, reached over REST. The API is described in
 * {@code external-systems/credit-bureau/credit-bureau-api/credit-bureau.yaml} of this
 * blueprint, and {@link ScoreRequestsApi} is the client generated from it.
 *
 * <p>
 * The bureau answers in two steps. This class asks the question and tells the bureau where to
 * send the score. The score arrives later at {@link CallbackController}, which belongs to this
 * adapter as well. Both halves together are what the port {@link CreditBureau} promises.
 * </p>
 */
public class CreditBureauRestAdapter implements CreditBureau {

  private final ScoreRequestsApi creditBureau;

  private final String callbackUrl;

  /**
   * Creates the adapter.
   *
   * @param creditBureau The generated client, whose base address is the credit bureau.
   * @param callbackUrl  Where the credit bureau sends the score: the address of
   *                     {@link CallbackController}, as the bureau reaches it.
   */
  public CreditBureauRestAdapter(
      final ScoreRequestsApi creditBureau,
      final String callbackUrl) {

    this.creditBureau = creditBureau;
    this.callbackUrl = callbackUrl;

  }

  @Override
  public void requestScore(
      final String loanRequestId,
      final int amount) {

    try {

      creditBureau.requestScore(new ScoreRequest(loanRequestId, amount, callbackUrl));

    } catch (ProcessingException | WebApplicationException e) {

      throw new IllegalStateException("The credit bureau did not take the question about loan request '"
          + loanRequestId
          + "'", e);

    }

  }

}
