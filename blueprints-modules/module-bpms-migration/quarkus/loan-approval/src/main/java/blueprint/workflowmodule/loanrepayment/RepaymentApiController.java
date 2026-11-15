package blueprint.workflowmodule.loanrepayment;

import java.util.UUID;

import blueprint.workflowmodule.loanrepayment.api.LoanRepaymentApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the repayment use case, GET only like every other one in this repository.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanRepaymentApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-repayment.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class RepaymentApiController implements LoanRepaymentApi {

  @Inject
  RepaymentService loanRepayment;

  /**
   * Starts a repayment, the workflow which stays in the old BPMS.
   *
   * @param amount The amount owed.
   * @return The id of the repayment started.
   */
  @Override
  public String start(
      final Integer amount) {

    final var repaymentId = UUID.randomUUID().toString();

    loanRepayment.initiate(repaymentId, amount);

    log.info(
        "Which BPMS runs it -> http://localhost:8080/api/loan-repayment/{}/bpms",
        repaymentId);

    return repaymentId;

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The adapter id of the BPMS holding the workflow.
   */
  @Override
  public String bpms(
      final String repaymentId) {

    return loanRepayment
        .bpmsHolding(repaymentId)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String repaymentId) {

    return loanRepayment
        .get(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
