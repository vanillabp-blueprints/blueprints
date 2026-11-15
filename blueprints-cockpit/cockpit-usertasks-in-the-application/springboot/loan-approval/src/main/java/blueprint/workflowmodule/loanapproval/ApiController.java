package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case for people: GET requests only, so a loan approval can be started
 * and looked at in a browser. The forms of the user tasks have endpoints of their own, in
 * {@link UserTaskFormController}.
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
@RestController
public class ApiController implements LoanApprovalApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The URLs of the forms are logged
   * once the user tasks are open.
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
   * Shows what the process did: the aggregate, with every user task and its data.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .describe(loanRequestId)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

}
