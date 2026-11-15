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
 * implemented by a BPMN process is none of its business.
 * </p>
 *
 * <p>
 * The endpoint counting statements is the exception, and it is one on purpose: it observes
 * the database, not the loan, so it asks {@link DatabaseTraffic} directly. Routing a
 * measurement through the business service would put something into that service which has
 * nothing to do with the use case.
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

  @Autowired
  private DatabaseTraffic traffic;

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
    log.info(
        "The loan is paid out after the withdrawal period. Until then nothing of this"
            + " application runs, which this URL shows ->"
            + " http://localhost:8080/api/loan-approval/statements");

    return loanRequestId;

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
   * How many statements the database was asked so far. Open it, let the workflow wait, open
   * it again: the difference is what this application costs while nothing happens.
   *
   * @return The number of statements, as a sentence for the browser.
   */
  @Override
  public String statements() {

    final var statements = traffic.statementsSoFar();

    log.info("The database was asked {} statements so far", statements);

    return statements
        + " statements so far";

  }

}
