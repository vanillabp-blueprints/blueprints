package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import blueprint.workflowmodule.loanapproval.api.StartFormApi;
import blueprint.workflowmodule.loanapproval.api.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.api.model.StartedLoanRequest;

/**
 * The endpoint the start form calls. It implements
 * {@code src/main/resources/openapi/loan-approval.yaml} of this module, which the frontend reads
 * to generate its client.
 *
 * <p>
 * The form opens from a button in the header of the Business Cockpit. It runs in the browser,
 * inside the cockpit, and calls {@code /wm/loan-approval/api/loan-requests}, a path of the
 * cockpit, which is the server the OpenAPI names. The cockpit forwards the request to the
 * {@code workflow-module-uri} of this module and puts the path of that address in place of
 * {@code /wm/loan-approval}. The address ends with {@code /loan-approval}, so the request arrives
 * here as {@code /loan-approval/api/loan-requests}.
 * </p>
 *
 * <p>
 * The form sends a body, so the endpoint is a POST. GET requests are for a person who types a
 * URL into the browser, see {@link ApiController}.
 * </p>
 *
 * <p>
 * The path and the records {@link LoanRequest} and {@link StartedLoanRequest} come from
 * {@link StartFormApi}, the interface the build generates from the same OpenAPI file. This class
 * only implements it. The OpenAPI names its paths below the server {@code /wm/loan-approval}, so
 * the interface maps {@code /api/loan-requests}. The {@code @RequestMapping} of this class puts
 * {@code /loan-approval} in front, which is where the request arrives.
 * </p>
 */
@RestController
@RequestMapping("/loan-approval")
public class StartFormController implements StartFormApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval with what the user typed into the start form ({@code requestLoan}).
   * The cockpit learns about the new case from the cockpit adapter, like about every other case.
   *
   * <p>
   * The generated record does not check its values. The OpenAPI says that the applicant must
   * not be empty and that the amount must be positive, so this method refuses anything else with
   * 400, before a process is started. The answer 201 comes from the interface, which carries
   * {@code @ResponseStatus} as the OpenAPI says.
   * </p>
   *
   * @param loanRequest What the user typed into the start form.
   * @return The id of the new loan request.
   */
  @Override
  public StartedLoanRequest requestLoan(
      final LoanRequest loanRequest) {

    if ((loanRequest.applicant() == null) || loanRequest
        .applicant()
        .isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The applicant is missing");
    }
    if ((loanRequest.amount() == null) || (loanRequest.amount() < 1)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The amount has to be a positive number");
    }

    final var loanRequestId = loanApproval.request(loanRequest
        .applicant()
        .strip(), loanRequest.amount());

    return new StartedLoanRequest(loanRequestId);

  }

}
