package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.InterestRate;
import blueprint.workflowmodule.loanapproval.model.InterestRateRepository;
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
 * Publishing the rate is where a signal reads differently from a message: the method takes
 * no loan approval. It is news about the market, not about one business case, and the
 * broadcast reaches whoever happens to be waiting for it.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow and broadcasting a signal have to run in a transaction. It is
 * deliberately absent from the methods a task handler calls: VanillaBP already runs a task
 * in a transaction it owns, and it commits that transaction for a {@code TaskException} on
 * purpose. A transaction declared here would roll back instead and throw away what the
 * handler wrote for the process to react to. VanillaBP sees the transaction it can no
 * longer commit and fails the task naming it, so the mistake shows up rather than costing
 * data.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  InterestRateRepository interestRates;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

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
        "Credit rating of loan approval '{}' is {}. The workflow now waits for today's interest rate,"
            + " and so does every other loan approval which got this far:"
            + "\n  Publish -> http://localhost:8080/api/loan-approval/publish-interest-rate?rate=3.5",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * Today's interest rate is published. Every loan approval waiting for it continues, and
   * the caller neither names nor knows them.
   *
   * <p>
   * The order of the two statements is the point: the rate is stored FIRST, and the signal
   * is broadcast afterwards. A signal transports nothing at all, so the only way a waiting
   * workflow learns the rate is by reading it - and it does that the moment the signal
   * reaches it.
   * </p>
   *
   * <p>
   * Publishing twice is harmless and needs no guard like the repeated message in
   * {@code bpmn-message-correlation} does. The rate of a day is overwritten, and a signal
   * nobody waits for reaches nobody.
   * </p>
   *
   * @param percentage The rate, in percent.
   */
  @Transactional
  public void publishInterestRate(
      final BigDecimal percentage) {

    interestRates
        .getEntityManager()
        .merge(InterestRate
            .builder()
            .publishedOn(LocalDate.now())
            .percentage(percentage)
            .build());

    workflow.interestRatePublished();

    log.info(
        "An interest rate of {}% was published. Every loan approval waiting for it continues",
        percentage);

  }

  /**
   * Offers the loan at the published rate, which is what the service task behind the signal
   * event triggers.
   *
   * <p>
   * This is where the data a signal cannot carry comes from: the application's own table.
   * There is always a rate to find here, because the signal that let this workflow continue
   * was broadcast after one had been stored.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void applyInterestRate(
      final Aggregate loanApproval) {

    final var publishedRate = interestRates
        .findLatestPublished()
        .orElseThrow(() -> new IllegalStateException(
            "No interest rate was published, so no signal can have reached loan approval '"
                + loanApproval.getLoanRequestId()
                + "'"));

    loanApproval.setInterestRate(publishedRate.getPercentage());

    log.info(
        "Loan approval '{}' is offered at {}%",
        loanApproval.getLoanRequestId(),
        publishedRate.getPercentage());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
