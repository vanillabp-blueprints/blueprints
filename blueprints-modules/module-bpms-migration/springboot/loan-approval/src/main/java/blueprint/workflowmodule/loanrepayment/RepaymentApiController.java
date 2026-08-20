package blueprint.workflowmodule.loanrepayment;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * The API of the repayment use case, GET only like every other one in this repository.
 */
@Slf4j
@RestController
@RequestMapping("/api/loan-repayment")
public class RepaymentApiController {

  @Autowired
  private RepaymentService service;

  /**
   * Starts a repayment, the workflow which stays in the old BPMS.
   *
   * @param amount The amount owed.
   * @return The id of the repayment started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "500") final int amount) {

    final var repaymentId = UUID.randomUUID().toString();

    service.initiateRepayment(repaymentId, amount);

    log.info(
        "Which BPMS runs it -> http://localhost:8080/api/loan-repayment/{}/bpms",
        repaymentId);

    return repaymentId;

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The adapter id of the BPMS holding the workflow.
   */
  @GetMapping("/{repaymentId}/bpms")
  public String bpms(
      @PathVariable final String repaymentId) {

    return service
        .bpmsHolding(repaymentId)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @GetMapping("/{repaymentId}")
  public String show(
      @PathVariable final String repaymentId) {

    return service
        .getRepayment(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
