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
 * The endpoint answering the partner is the one that matters here: it opens the transaction
 * of the application, and it may run while the BPMS is inside the transaction of the other
 * branch.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service service;

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

    return loanRequestId;

  }

  /**
   * Answers the open partner task, which lets that branch continue.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param approvedBy    Who approved.
   * @return What was done, for the browser to show.
   */
  @GetMapping("/{loanRequestId}/approve")
  public String approve(
      @PathVariable final String loanRequestId,
      @RequestParam(defaultValue = "partner") final String approvedBy) {

    service.approvePartnerRequest(loanRequestId, approvedBy);

    return "The partner approved loan approval '"
        + loanRequestId
        + "'";

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
