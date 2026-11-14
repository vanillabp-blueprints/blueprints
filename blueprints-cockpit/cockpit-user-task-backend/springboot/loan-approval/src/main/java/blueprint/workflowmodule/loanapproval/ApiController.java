package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanRequestsApi;
import lombok.extern.slf4j.Slf4j;

/**
 * The API which starts a loan approval and shows it. It consists of GET requests only, so it
 * works in a browser. The forms of the user tasks have an API of their own, in
 * {@link UserTaskFormController}.
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
@Slf4j
@RestController
public class ApiController implements LoanRequestsApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember. The two user tasks then show up
   * in the Business Cockpit.
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
        "Show the result -> http://localhost:8080/loan-approval/api/loan-requests/{}",
        loanRequestId);

    return loanRequestId;

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
