package blueprint.workflowmodule.loanrepayment;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.commons.CustomerDirectory;
import blueprint.commons.Money;
import blueprint.workflowmodule.loanrepayment.config.LoanRepaymentProperties;
import blueprint.workflowmodule.loanrepayment.model.Aggregate;
import blueprint.workflowmodule.loanrepayment.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the second use case: a granted loan is paid back, and the process
 * works out the installments.
 *
 * <p>
 * It knows nothing about loan approvals. The two modules live in one application and share
 * a database, and still neither imports a class of the other: what they have in common is
 * the library {@code banking-commons}, which holds a value object and a client, and nothing
 * a process is made of.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service("loanRepaymentService")
public class Service {

  @Autowired
  private AggregateRepository repayments;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanRepaymentProperties properties;

  @Autowired
  private CustomerDirectory customers;

  /**
   * A granted loan enters repayment.
   *
   * @param repaymentId The natural id of the repayment.
   * @param customerId  The customer paying it back.
   * @param amount      The amount owed.
   */
  @Transactional
  public void initiateRepayment(
      final String repaymentId,
      final String customerId,
      final int amount) {

    final var repayment = Aggregate
        .builder()
        .repaymentId(repaymentId)
        .customerId(customerId)
        .amount(amount)
        .build();

    workflow.repaymentAgreed(repayment);

    log.info(
        "Repayment '{}' started: {} pays back {}",
        repaymentId,
        customers.nameOf(customerId),
        Money.euro(amount).formatted());

  }

  /**
   * Works out how many installments the repayment is split into.
   *
   * @param repayment The repayment to schedule.
   */
  public void scheduleInstallments(
      final Aggregate repayment) {

    final var installments = Math.min(
        properties.getMaxInstallments(),
        Math.max(1, repayment.getAmount() / 1000));

    repayment.setInstallments(installments);

    log.info(
        "Repayment '{}' is paid back in {} installments",
        repayment.getRepaymentId(),
        installments);

  }

  /**
   * The state of a repayment, as far as the process has come.
   *
   * @param repaymentId The natural id of the repayment.
   * @return The repayment, if it exists.
   */
  public Optional<Aggregate> getRepayment(
      final String repaymentId) {

    return repayments.findById(repaymentId);

  }

}
