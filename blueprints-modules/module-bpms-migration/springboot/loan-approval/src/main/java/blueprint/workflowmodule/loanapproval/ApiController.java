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
 * The endpoints are the two wait states of the process plus the two reads. Which BPMS runs
 * the workflow behind them is never a parameter, and that is what a migration looks like
 * from the outside: nothing.
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
   * Answers the risk assessment, which is the user task the process waits at.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, logged when it was created.
   * @return What happened.
   */
  @GetMapping("/{loanRequestId}/assess-risk/{taskId}")
  public String assessRisk(
      @PathVariable final String loanRequestId,
      @PathVariable final String taskId) {

    service.assessRisk(loanRequestId, taskId);

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
  @GetMapping("/{loanRequestId}/contract-signed")
  public String contractSigned(
      @PathVariable final String loanRequestId,
      @RequestParam(defaultValue = "Jane Doe") final String signedBy) {

    service.contractSigned(loanRequestId, signedBy);

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
  @GetMapping("/{loanRequestId}/bpms")
  public String bpms(
      @PathVariable final String loanRequestId) {

    return service
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
