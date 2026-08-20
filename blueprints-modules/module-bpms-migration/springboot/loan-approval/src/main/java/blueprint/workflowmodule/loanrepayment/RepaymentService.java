package blueprint.workflowmodule.loanrepayment;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanrepayment.model.Repayment;
import blueprint.workflowmodule.loanrepayment.model.RepaymentRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the repayment use case. Deliberately small: this workflow is part
 * of the blueprint because of where it runs, not because of what it does.
 */
@Slf4j
@org.springframework.stereotype.Service
public class RepaymentService {

  @Autowired
  private RepaymentRepository repayments;

  @Autowired
  private RepaymentWorkflow workflow;

  /**
   * A repayment is due.
   *
   * @param repaymentId The natural id of the repayment.
   * @param amount      The amount owed.
   */
  @Transactional
  public void initiateRepayment(
      final String repaymentId,
      final int amount) {

    final var repayment = Repayment
        .builder()
        .repaymentId(repaymentId)
        .amount(amount)
        .build();

    workflow.repaymentDue(repayment);

    log.info("Repayment '{}' started", repaymentId);

  }

  /**
   * Books the instalment, which is all this process does.
   *
   * @param repayment The workflow's aggregate.
   */
  public void bookInstalment(
      final Repayment repayment) {

    repayment.setInstalmentBooked(Boolean.TRUE);

    log.info(
        "Instalment of repayment '{}' was booked",
        repayment.getRepaymentId());

  }

  /**
   * @param repaymentId The natural id of the repayment.
   * @return The adapter id of the BPMS running this workflow.
   */
  @Transactional
  public Optional<String> bpmsHolding(
      final String repaymentId) {

    return repayments
        .findById(repaymentId)
        .map(repayment -> {
          final List<String> adapters = workflow
              .definitionsOf(repayment)
              .stream()
              .map(definition -> definition
                  .id()
                  .split("#")[0])
              .distinct()
              .toList();
          return adapters.isEmpty()
              ? "unknown"
              : String.join(", ", adapters);
        });

  }

  /**
   * @param repaymentId The natural id of the repayment.
   * @return The repayment, if it exists.
   */
  @Transactional
  public Optional<Repayment> getRepayment(
      final String repaymentId) {

    return repayments.findById(repaymentId);

  }

}
