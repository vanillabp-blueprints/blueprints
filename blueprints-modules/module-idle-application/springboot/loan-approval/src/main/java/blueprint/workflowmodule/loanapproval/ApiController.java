package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service service;

  @Autowired
  private DatabaseTraffic traffic;

  /**
   * Starts a loan approval. This is the one URL the README names.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "5000") final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, amount);

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
  @GetMapping("/{loanRequestId}")
  public String show(
      @PathVariable final String loanRequestId) {

    return service
        .getLoanApproval(loanRequestId)
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
  @GetMapping("/statements")
  public String statements() {

    final var statements = traffic.statementsSoFar();

    log.info("The database was asked {} statements so far", statements);

    return statements
        + " statements so far";

  }

}
