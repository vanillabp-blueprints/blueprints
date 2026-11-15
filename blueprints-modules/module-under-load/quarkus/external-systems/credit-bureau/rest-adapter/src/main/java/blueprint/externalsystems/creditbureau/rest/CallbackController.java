package blueprint.externalsystems.creditbureau.rest;

import blueprint.externalsystems.creditbureau.callback.server.ScoreCallbackApi;
import blueprint.externalsystems.creditbureau.callback.server.model.Score;
import blueprint.workflowmodule.loanapproval.Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

/**
 * Where the credit bureau sends a score. It is the second half of the adapter: the bureau calls
 * this endpoint, and the endpoint hands the score to the {@link Service} of the workflow
 * module, which takes it as a message for the waiting workflow.
 *
 * <p>
 * The path, the method and the JSON come from {@link ScoreCallbackApi}, the interface generated
 * from {@code credit-bureau-callback.yaml}. This class only implements it. The endpoint is a
 * POST with a JSON body, because the credit bureau decides what its callback looks like. The
 * rule that a person uses GET requests is about the API a person uses in a browser, and nobody
 * types a callback.
 * </p>
 */
@ApplicationScoped
public class CallbackController implements ScoreCallbackApi {

  private final Service loanApproval;

  /**
   * Creates the controller.
   *
   * @param loanApproval The service of the workflow module, which takes the score.
   */
  public CallbackController(
      final Service loanApproval) {

    this.loanApproval = loanApproval;

  }

  /**
   * Takes one score of the credit bureau.
   *
   * @param score The score, and the loan request it is about.
   * @return 204 if the application took it, 404 if it does not know the loan request.
   */
  @Override
  public Response reportScore(
      final Score score) {

    try {

      loanApproval.creditScoreReceived(score.getLoanRequestId(), score.getScore());
      return Response
          .noContent()
          .build();

    } catch (IllegalArgumentException e) {

      // The service names a loan request it does not know with this exception.
      return Response
          .status(Response.Status.NOT_FOUND)
          .build();

    }

  }

}
