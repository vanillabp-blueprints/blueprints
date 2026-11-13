package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;

import blueprint.workflowmodule.loanapproval.Service.Change;
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
 * request. The endpoints are written by hand, with the type as a record below, rather than
 * generated from the OpenAPI. The integration test reads the OpenAPI and checks that every
 * operation in it has its endpoint here.
 * </p>
 */
@RestController
@RequestMapping("/loan-approval/api/cases/{loanRequestId}")
public class CasePageController {

  @Autowired
  private Service loanApproval;

  /**
   * What the case page shows in its information area ({@code getCase}).
   *
   * @param loanRequestId The id of the loan request, the business id of the case.
   * @return The data of the case.
   */
  @GetMapping
  public Case getCase(
      @PathVariable final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(CasePageController::caseOf)
        .orElseThrow(() -> unknown(loanRequestId));

  }

  /**
   * Cancels the case ({@code cancelCase}). The module correlates the message which starts the
   * event subprocess "Cancel case". The cockpit learns that the case ended the way it learns
   * about every other end, from the cockpit adapter.
   *
   * @param loanRequestId The id of the loan request, the business id of the case.
   */
  @PostMapping("/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelCase(
      @PathVariable final String loanRequestId) {

    final var change = loanApproval.cancel(loanRequestId);
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

    final var outcome = loanRequest.getOutcome() == null
        ? null
        : loanRequest.getOutcome().name();
    return new Case(
        loanRequest.getLoanRequestId(), loanRequest.getApplicant(), loanRequest.getAmount(), outcome);

  }

  private static ResponseStatusException unknown(
      final String loanRequestId) {

    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, "No case '"
            + loanRequestId
            + "'");

  }

  /**
   * What the case page shows. A value which is not there is left out of the JSON, as the
   * OpenAPI describes it, rather than sent as null.
   *
   * @param loanRequestId The id of the loan request.
   * @param applicant     Who asks for the loan.
   * @param amount        The amount requested.
   * @param outcome       How the case ended, {@code APPROVED} or {@code CANCELLED}. Missing
   *                      while it runs.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Case(
                     String loanRequestId,
                     String applicant,
                     Integer amount,
                     String outcome) {
  }

}
