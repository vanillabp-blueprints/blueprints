package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanRequestsApi;

/**
 * The API for a human with a browser: it starts a loan approval, approves it and shows it. It
 * consists of GET requests only, so it works in a browser. The start form in the header of the
 * cockpit has an API of its own, in {@link StartFormController}.
 *
 * <p>
 * Every path of this module starts with {@code /loan-approval}, the workflow module ID. The
 * Business Cockpit forwards requests for this module to that path, and an application which
 * holds several workflow modules keeps their paths apart this way.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanRequestsApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-requests.yaml}. This class
 * only implements it.
 * </p>
 */
@RestController
public class ApiController implements LoanRequestsApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval without the cockpit. This is the one URL to remember. The start form
   * in the header of the cockpit does the same with a POST.
   *
   * @param applicant Who asks for the loan.
   * @param amount    The amount requested.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final String applicant,
      final Integer amount) {

    return loanApproval.request(applicant, amount);

  }

  /**
   * Approves the loan request, which completes the user task. The case ends.
   *
   * <p>
   * In a real application, the form of the user task does this. Forms are the subject of the
   * blueprint {@code cockpit-user-task}, so this blueprint has none. This URL stands in for it.
   * </p>
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What happened.
   */
  @Override
  public String approve(
      final String loanRequestId) {

    return switch (loanApproval.approve(loanRequestId)) {
      case DONE -> "approved";
      case NOT_FOUND -> "unknown loan request '"
          + loanRequestId
          + "'";
      case NOT_POSSIBLE -> "the loan request '"
          + loanRequestId
          + "' is not waiting for a review";
    };

  }

  /**
   * Shows what the process did.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(Object::toString)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

}
