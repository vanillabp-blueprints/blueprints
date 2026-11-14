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
 * It talks to {@link Service} and to nothing else. That the use case happens to be
 * implemented by a BPMN process is none of its business.
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
   * The signed contract comes back. This is the callback a partner, a portal or a
   * document service would call, and the message it carries reaches the process as a name
   * plus whatever this method put onto the aggregate.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param signedBy      Who signed the contract.
   * @return What was done, for the browser to show.
   */
  @Override
  public String contractSigned(
      final String loanRequestId,
      final String signedBy) {

    loanApproval.contractSigned(loanRequestId, signedBy);

    return "The contract of loan approval '"
        + loanRequestId
        + "' was signed";

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
