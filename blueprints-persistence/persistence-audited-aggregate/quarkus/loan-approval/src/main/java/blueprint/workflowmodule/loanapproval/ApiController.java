package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import blueprint.workflowmodule.loanapproval.audit.ChangeBeingMade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be
 * walked through in a browser - no tooling, no request bodies.
 *
 * <p>
 * It talks to {@link Service} and to nothing else, and it says who is acting while it
 * does. The name comes in with the request because this blueprint has nobody logged in;
 * an application with a security framework reads the authenticated user, in a filter, and
 * then the API has nothing to do with the auditing either. What matters is where it sits:
 * around the call which opens the transaction, because Envers writes the revision when
 * that transaction commits.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-approval.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements LoanApprovalApi {

  @Inject
  Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs continuing the
   * process are logged once the risk assessment is open.
   *
   * @param amount      The amount requested.
   * @param requestedBy Who asks for the loan, which is what the trail will name.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final Integer amount,
      final String requestedBy) {

    final var loanRequestId = UUID.randomUUID().toString();

    ChangeBeingMade.attributeTo(
        requestedBy,
        () -> loanApproval.request(loanRequestId, amount, requestedBy));

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Answers the open risk assessment, which completes the user task and lets the process
   * continue to the payout.
   *
   * @param loanRequestId    The id returned by starting the process.
   * @param taskId           The id of the user task, taken from the logged URL.
   * @param riskIsAcceptable What the assessment concluded.
   * @param decidedBy        Who assessed it, which is what the trail will name.
   * @return What was done, for the browser to show.
   */
  @Override
  public String assessRisk(
      final String loanRequestId,
      final String taskId,
      final Boolean riskIsAcceptable,
      final String decidedBy) {

    ChangeBeingMade.attributeTo(
        decidedBy,
        () -> loanApproval.assessRisk(loanRequestId, taskId, riskIsAcceptable, decidedBy));

    return "The risk of loan approval '"
        + loanRequestId
        + "' was assessed";

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

  /**
   * Shows every state the loan approval went through and who caused it. The answer of
   * the auditing to the question this blueprint is about.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The trail, oldest change first.
   */
  @Override
  public String trail(
      final String loanRequestId) {

    final var trail = loanApproval.getTrail(loanRequestId);

    return trail.isEmpty()
        ? "unknown loan request '"
            + loanRequestId
            + "'"
        : String.join("\n", trail);

  }

}
