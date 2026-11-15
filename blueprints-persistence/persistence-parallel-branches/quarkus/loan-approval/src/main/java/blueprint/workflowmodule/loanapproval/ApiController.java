package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser - no tooling, no request bodies.
 *
 * <p>
 * The endpoint answering the partner is the one that matters here: it opens the transaction
 * of the application, and it may run while the BPMS is inside the transaction of the other
 * branch.
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
   * Starts a loan approval. This is the one URL the README names.
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
   * Answers the open partner task, which lets that branch continue.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param approvedBy    Who approved.
   * @return What was done, for the browser to show.
   */
  @Override
  public String approve(
      final String loanRequestId,
      final String approvedBy) {

    loanApproval.approvePartnerRequest(loanRequestId, approvedBy);

    return "The partner approved loan approval '"
        + loanRequestId
        + "'";

  }

  /**
   * Says that the documents arrived, which lets the other branch read them.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What was done, for the browser to show.
   */
  @Override
  public String documentsReady(
      final String loanRequestId) {

    loanApproval.documentsReady(loanRequestId);

    return "The documents of loan approval '"
        + loanRequestId
        + "' arrived";

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
