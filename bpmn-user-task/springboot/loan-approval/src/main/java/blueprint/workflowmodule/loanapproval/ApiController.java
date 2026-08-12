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
 * implemented by a BPMN process is none of its business, and the task id it passes on is
 * an id of the business case as far as this class is concerned.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service service;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs continuing the
   * process are logged once the risk assessment is open.
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
  @GetMapping("/{loanRequestId}/assess-risk/{taskId}")
  public String assessRisk(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId,
      @RequestParam(defaultValue = "true") final boolean riskIsAcceptable) {

    service.assessRisk(loanRequestId, taskId, riskIsAcceptable);

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
  @GetMapping("/{loanRequestId}/withdraw/{taskId}")
  public String withdraw(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId) {

    service.withdrawLoanRequest(loanRequestId, taskId);

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

}
