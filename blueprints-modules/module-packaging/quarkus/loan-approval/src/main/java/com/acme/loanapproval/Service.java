package com.acme.loanapproval;

import java.util.Optional;

import com.acme.banking.CustomerDirectory;
import com.acme.banking.Money;
import com.acme.loanapproval.config.LoanApprovalProperties;
import com.acme.loanapproval.model.Aggregate;
import com.acme.loanapproval.model.AggregateRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, {@code loanRequested} rather than "start the process", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * Both directions meet here, and that is the point: this is the one class describing the
 * use case, and it does so without naming a single BPMN element.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow has to run in a transaction and reading an entity needs one too. It
 * is deliberately absent from the methods a task handler calls: VanillaBP already runs a
 * task in a transaction it owns, and it commits that transaction for a
 * {@code TaskException} on purpose. A transaction declared here would roll back instead and
 * throw away what the handler wrote for the process to react to. VanillaBP sees the
 * transaction it can no longer commit and fails the task naming it, so the mistake shows up
 * rather than costing data.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * From the shared library, and the reason it exists: the other use case asks the same
   * system for the same thing, and neither of them should own the way there.
   */
  @Inject
  CustomerDirectory customers;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param customerId    The customer asking for the loan.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final String customerId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .customerId(customerId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info(
        "Loan approval '{}' started: {} asks for {}",
        loanRequestId,
        customers.nameOf(customerId),
        Money.euro(amount).formatted());

  }

  /**
   * Rates a loan request. A real application would ask a rating service here; what matters
   * for the blueprint is where this code sits: in the business service, not in the
   * {@code @WorkflowTask} method which happens to trigger it.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.ratingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}, provided by '{}'",
        loanApproval.getLoanRequestId(),
        rating,
        properties.ratingProvider());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  @Transactional
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
