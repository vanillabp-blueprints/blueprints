package blueprint.workflowmodule.loanrepayment;

import java.util.List;
import java.util.Optional;

import blueprint.workflowmodule.loanrepayment.model.Repayment;
import blueprint.workflowmodule.loanrepayment.model.RepaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the repayment use case. Deliberately small: this workflow is part
 * of the blueprint because of where it runs, not because of what it does.
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Transactional
public class RepaymentService {

  @Inject
  RepaymentRepository repayments;

  @Inject
  RepaymentWorkflow workflow;

  /**
   * A repayment is due.
   *
   * @param repaymentId The natural id of the repayment.
   * @param amount      The amount owed.
   */
  public void initiate(
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
  public Optional<String> bpmsHolding(
      final String repaymentId) {

    return repayments
        .findByIdOptional(repaymentId)
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
  public Optional<Repayment> get(
      final String repaymentId) {

    return repayments.findByIdOptional(repaymentId);

  }

}
