package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import blueprint.workflowmodule.loanapproval.Service.Change;
import blueprint.workflowmodule.loanapproval.api.CasesApi;
import blueprint.workflowmodule.loanapproval.api.model.Case;
import blueprint.workflowmodule.loanapproval.model.Aggregate;

/**
 * The endpoints the case page calls. They implement
 * {@code src/main/resources/openapi/loan-approval.yaml} of this module, which the frontend reads
 * to generate its client.
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
 * The paths and the record {@link Case} come from {@link CasesApi}, the interface the build
 * generates from the same OpenAPI file. This class only implements it. The OpenAPI names its
 * paths below the server {@code /wm/loan-approval}, so the interface maps
 * {@code /api/cases/...}. The {@code @RequestMapping} of this class puts {@code /loan-approval}
 * in front, which is where the request arrives.
 * </p>
 */
@RestController
@RequestMapping("/loan-approval")
public class CasePageController implements CasesApi {

  @Autowired
  private Service loanApproval;

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
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The case '"
              + loanRequestId
              + "' has ended already");
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

  private static ResponseStatusException unknown(
      final String loanRequestId) {

    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "No case '"
            + loanRequestId
            + "'");

  }

}
