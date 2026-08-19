package com.acme.loanrepayment;

import java.util.Optional;

import com.acme.banking.CustomerDirectory;
import com.acme.banking.Money;
import com.acme.loanrepayment.config.LoanRepaymentProperties;
import com.acme.loanrepayment.model.Aggregate;
import com.acme.loanrepayment.model.AggregateRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
@ApplicationScoped
public class Service {

  @Inject
  AggregateRepository repayments;

  @Inject
  Workflow workflow;

  @Inject
  LoanRepaymentProperties properties;

  @Inject
  CustomerDirectory customers;

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
        properties.maxInstallments(),
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
  @Transactional
  public Optional<Aggregate> getRepayment(
      final String repaymentId) {

    return repayments.findByIdOptional(repaymentId);

  }

}
