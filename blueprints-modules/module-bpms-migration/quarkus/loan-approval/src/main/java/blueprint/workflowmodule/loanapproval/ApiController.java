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
 * The endpoints are the two wait states of the process plus the two reads. Which BPMS runs
 * the workflow behind them is never a parameter, and that is what a migration looks like
 * from the outside: nothing.
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
   * Answers the risk assessment, which is the user task the process waits at.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, logged when it was created.
   * @return What happened.
   */
  @Override
  public String assessRisk(
      final String loanRequestId,
      final String taskId) {

    loanApproval.assessRisk(loanRequestId, taskId);

    return "risk of '"
        + loanRequestId
        + "' assessed";

  }

  /**
   * Reports the signed contract, which is the message the process waits for.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param signedBy      Who signed.
   * @return What happened.
   */
  @Override
  public String contractSigned(
      final String loanRequestId,
      final String signedBy) {

    loanApproval.contractSigned(loanRequestId, signedBy);

    return "contract of '"
        + loanRequestId
        + "' signed by "
        + signedBy;

  }

  /**
   * Shows which BPMS runs this workflow, which is what this blueprint is about.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The adapter id of the BPMS holding the workflow.
   */
  @Override
  public String bpms(
      final String loanRequestId) {

    return loanApproval
        .bpmsHolding(loanRequestId)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

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
