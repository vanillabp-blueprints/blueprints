package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.service.TaskException;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan
 * approval, expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP, with one exception which is the subject of this blueprint:
 * {@link TaskException} tells the process that a business case ended in a way the BPMN
 * models. That is not a technical detail leaking in, it is the vocabulary the business
 * shares with the model.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * {@link #assessCreditRating} throws a {@code TaskException} when the rating is too low, and
 * that is the subject of this blueprint. So it says
 * {@code @Transactional(noRollbackFor = TaskException.class)}. VanillaBP commits its
 * transaction for that exception on purpose, so what the method wrote before is kept for the
 * process to react to. A plain {@code @Transactional} would mark the transaction for rollback
 * instead. VanillaBP sees the transaction it can no longer commit and fails the task naming
 * it, so the mistake shows up rather than costing data.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
@Transactional
public class Service {

  /**
   * The BPMN error raised for a rating below the minimum. The same string is the error
   * code of the error boundary event in the BPMN, and there is no second place it is
   * written down.
   */
  public static final String LOAN_REJECTED = "loan-rejected";

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private CreditRatingClient creditRatings;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  public void request(
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
   * Rates a loan request by asking the rating provider, which is the work behind the
   * service task. Three outcomes are possible and each one leaves the workflow somewhere
   * else:
   *
   * <ul>
   * <li>the provider answers and the rating is good enough: the method returns, the
   * aggregate is saved and the process moves on,</li>
   * <li>the provider does not answer: {@link CreditRatingUnavailableException} leaves this
   * method, nothing is saved and the BPMS delivers the task again,</li>
   * <li>the rating is too low: a {@link TaskException} names the BPMN error, and what was
   * written before throwing is saved nevertheless.</li>
   * </ul>
   *
   * <p>
   * The first thing the method does is to check whether there is a rating already. A
   * remote BPMS may deliver a task more than once, and asking a provider twice for the
   * same loan may cost money or leave a duplicate on their side. Idempotency is keyed on
   * the state of the aggregate, never on a counter of invocations.
   * </p>
   *
   * @param loanApproval The loan approval to rate.
   * @param provider     The rating provider to ask, mapped in the BPMN.
   */
  @Transactional(noRollbackFor = TaskException.class)
  public void assessCreditRating(
      final Aggregate loanApproval,
      final String provider) {

    if (loanApproval.getCreditRating() != null) {

      log.info(
          "Loan approval '{}' is rated already, nothing to do",
          loanApproval.getLoanRequestId());
      return;

    }

    final var rating = creditRatings.rate(
        provider,
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount());

    loanApproval.setCreditRating(rating);
    loanApproval.setRatedBy(provider);

    log.info(
        "Credit rating of loan approval '{}' is {}, awarded by '{}'",
        loanApproval.getLoanRequestId(),
        rating,
        provider);

    if (rating < properties.getMinimumRating()) {

      loanApproval.setRejectionReason("rating "
          + rating
          + " is below the minimum of "
          + properties.getMinimumRating());

      log.info(
          "Loan approval '{}' is rejected: {}",
          loanApproval.getLoanRequestId(),
          loanApproval.getRejectionReason());

      throw new TaskException(LOAN_REJECTED);

    }

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
