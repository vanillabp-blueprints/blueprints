package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.creditcheck.CreditChecks;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the loan approval: what the application can do with a loan
 * approval, without a single word about processes.
 *
 * <p>
 * It uses the credit check through {@link CreditChecks}, and that is all it knows about it.
 * It asks for a check, keeps the id it gets, and later takes the score which comes back. It
 * does not know that the credit check is a process, and it never touches its aggregate.
 * </p>
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
public class Service {

  /**
   * How the loan approval names itself when it asks for a credit check. The credit check
   * hands it back with the result, so {@link CreditCheckedListener} can tell its own results
   * from those of other processes.
   */
  public static final String CREDIT_CHECK_REQUESTER = "loan-approval";

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  CreditChecks creditChecks;

  @Inject
  LoanApprovalProperties properties;

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
   * Asks for a credit check. It is started in the same transaction, so the loan approval and
   * the credit check are committed together, or not at all.
   *
   * @param loanApproval The loan approval which needs the credit check.
   */
  public void requestCreditCheck(
      final Aggregate loanApproval) {

    final var creditCheckId = creditChecks.request(
        CREDIT_CHECK_REQUESTER,
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount());

    loanApproval.setCreditCheckId(creditCheckId);

    log.info(
        "Loan approval '{}' waits for credit check '{}'",
        loanApproval.getLoanRequestId(),
        creditCheckId);

  }

  /**
   * The credit check reported its result. This is the message the workflow waits for.
   *
   * <p>
   * The score is written onto the aggregate FIRST, and only then is the message correlated.
   * The BPMS learns the name of the message and nothing else.
   * </p>
   *
   * <p>
   * The early return makes a result which arrives twice harmless. Without a correlation id
   * VanillaBP does not drop a repeated message, so the application decides what a repeat
   * means.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param score         The score the credit check reported.
   */
  public void creditChecked(
      final String loanRequestId,
      final int score) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (loanApproval.getCreditScore() != null) {

      log.info(
          "The credit of loan approval '{}' was checked already, nothing to correlate",
          loanRequestId);
      return;

    }

    loanApproval.setCreditScore(score);

    workflow.creditChecked(loanApproval);

    log.info(
        "The credit check of loan approval '{}' reported a score of {}",
        loanRequestId,
        score);

  }

  /**
   * Decides on the loan. This is where the loan approval uses the result in its own way.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void decideOnLoan(
      final Aggregate loanApproval) {

    final var approved = loanApproval.getCreditScore() >= properties.minimumScore();

    loanApproval.setApproved(approved);

    log.info(
        "Loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        approved
            ? "approved"
            : "rejected");

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> get(
      final String loanRequestId) {

    return loanApprovals.findByIdOptional(loanRequestId);

  }

}
