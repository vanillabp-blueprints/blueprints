package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.Service.Change;
import blueprint.workflowmodule.loanapproval.api.CasesApi;
import blueprint.workflowmodule.loanapproval.api.model.Case;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * The endpoints the case page calls. They implement {@code
 * src/main/resources/loan-approval/openapi/loan-approval.yaml} of this module, which the frontend
 * reads to generate its client.
 *
 * <p>
 * The case page runs in the browser, inside the Business Cockpit. It calls
 * {@code /wm/loan-approval/api/cases/...}, a path of the cockpit, which is the server the OpenAPI
 * names. The cockpit forwards the request to the {@code workflow-module-uri} of this module and
 * puts the path of that address in place of {@code /wm/loan-approval}. The address ends with
 * {@code /loan-approval}, so the request arrives here as {@code /loan-approval/api/cases/...}.
 * </p>
 *
 * <p>
 * The page knows its case by the business id the cockpit shows, which is the id of the loan
 * request.
 * </p>
 *
 * <p>
 * The paths and the class {@link Case} come from {@link CasesApi}, the interface the build
 * generates from the same OpenAPI file. This class only implements it. The OpenAPI names its
 * paths below the server {@code /wm/loan-approval}, so the interface maps
 * {@code /api/cases/{loanRequestId}}. The {@code @Path} of this class puts {@code /loan-approval}
 * in front, which is where the request arrives. Jakarta REST takes the {@code @Path} of a class
 * instead of the one of its interface, not in addition to it. So the class repeats the path of
 * the interface after {@code /loan-approval}. The methods keep the paths of the interface.
 * </p>
 */
@ApplicationScoped
@Path("/loan-approval/api/cases/{loanRequestId}")
public class CasePageController implements CasesApi {

  @Inject
  Service loanApproval;

  /**
   * What the case page shows in its information area ({@code getCase}).
   *
   * @param loanRequestId The id of the loan request, the business id of the case.
   * @return The data of the case.
   */
  @Override
  public Case getCase(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(CasePageController::caseOf)
        .orElseThrow(() -> unknown(loanRequestId));

  }

  /**
   * Withdraws the loan request ({@code withdrawLoanRequest}). The module correlates the message
   * which starts the event subprocess "Withdraw loan request". The cockpit learns that the case
   * ended the way it learns about every other end, from the cockpit adapter.
   *
   * @param loanRequestId The id of the loan request, the business id of the case.
   */
  @Override
  public void withdrawLoanRequest(
      final String loanRequestId) {

    final var change = loanApproval.withdraw(loanRequestId);
    if (change == Change.NOT_FOUND) {
      throw unknown(loanRequestId);
    }
    if (change == Change.NOT_POSSIBLE) {
      throw new WebApplicationException(
          "The case '"
              + loanRequestId
              + "' has ended already", Status.CONFLICT);
    }

  }

  private static Case caseOf(
      final Aggregate loanRequest) {

    return new Case(
        loanRequest.getLoanRequestId(), loanRequest.getApplicant(), loanRequest.getAmount(), Case.StatusEnum
            .fromValue(loanRequest
                .getStatus()
                .name()));

  }

  private static WebApplicationException unknown(
      final String loanRequestId) {

    return new WebApplicationException(
        "No case '"
            + loanRequestId
            + "'", Status.NOT_FOUND);

  }

}
