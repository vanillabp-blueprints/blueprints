package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser - no tooling, no request bodies.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. That the use case happens to be
 * implemented by a BPMN process is none of its business, and the task id it passes on is
 * an id of the business case as far as this class is concerned.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-approval.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@RestController
public class ApiController implements LoanApprovalApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs continuing the
   * process are logged once the risk assessment is open.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final Integer amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Answers the open risk assessment, which completes the user task and lets the process
   * continue.
   *
   * @param loanRequestId    The id returned by starting the process.
   * @param taskId           The id of the user task, taken from the logged URL.
   * @param riskIsAcceptable What the assessment concluded.
   * @return What was done, for the browser to show.
   */
  @Override
  public String assessRisk(
      final String loanRequestId,
      final String taskId,
      final Boolean riskIsAcceptable) {

    loanApproval.assessRisk(loanRequestId, taskId, riskIsAcceptable);

    return "The risk of loan approval '"
        + loanRequestId
        + "' was assessed";

  }

  /**
   * Withdraws the request while the risk assessment is still open, which cancels the user
   * task by BPMN error.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, taken from the logged URL.
   * @return What was done, for the browser to show.
   */
  @Override
  public String withdraw(
      final String loanRequestId,
      final String taskId) {

    loanApproval.withdrawLoanRequest(loanRequestId, taskId);

    return "Loan approval '"
        + loanRequestId
        + "' was withdrawn";

  }

  /**
   * Shows what the process did, which is the second half of operating it in a browser.
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
