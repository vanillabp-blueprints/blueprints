package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
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
 * decides what this means for the BPMN. The other direction runs through the task handlers,
 * which call the methods below when a process reaches a task.
 * </p>
 *
 * <p>
 * There is one service for the whole use case, although two processes are involved. The
 * split into a calling and a called process is a decision about the model - it keeps a
 * diagram readable - and the business code has no reason to follow it. The methods below
 * are named after what happens in the business case, and nothing in them says which of the
 * two processes triggered them.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the method the API calls, because
 * starting a workflow has to run in a transaction. It is deliberately absent from the
 * methods a task handler calls: VanillaBP already runs a task in a transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared here would roll back instead and throw away what the handler wrote for the
 * process to react to. VanillaBP sees the transaction it can no longer commit and fails the
 * task naming it, so the mistake shows up rather than costing data.
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
   * Rates a loan request. A real application would ask a rating service here.
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
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * What the customer can put up as a security. First step of the risk assessment, which
   * the called process runs through.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkCollateral(
      final Aggregate loanApproval) {

    final var collateral = (loanApproval.getAmount() * properties.collateralPercentage()) / 100;

    loanApproval.setCollateralValue(collateral);

    log.info(
        "Collateral of loan approval '{}' is worth {}",
        loanApproval.getLoanRequestId(),
        collateral);

  }

  /**
   * How much of the customer's income is already spent on debt. Second step of the risk
   * assessment.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void checkDebtRatio(
      final Aggregate loanApproval) {

    final var debtRatio = Math.min(100, loanApproval.getAmount() / 500);

    loanApproval.setDebtRatio(debtRatio);

    log.info(
        "Debt ratio of loan approval '{}' is {}%",
        loanApproval.getLoanRequestId(),
        debtRatio);

  }

  /**
   * Decides on the loan, using what the risk assessment left on the aggregate. This is the
   * step after the call activity, and it shows what decomposition costs: nothing. The
   * result of the called process is on the aggregate, like every other attribute.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void decideOnLoan(
      final Aggregate loanApproval) {

    final var acceptable = (loanApproval.getCreditRating() >= properties.minimumRating()) && (loanApproval
        .getDebtRatio() <= properties.maximumDebtRatio());

    loanApproval.setDecision(acceptable ? "approved" : "rejected");

    log.info(
        "Loan approval '{}' was {} (rating {}, debt ratio {}%, collateral {})",
        loanApproval.getLoanRequestId(),
        loanApproval.getDecision(),
        loanApproval.getCreditRating(),
        loanApproval.getDebtRatio(),
        loanApproval.getCollateralValue());

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
