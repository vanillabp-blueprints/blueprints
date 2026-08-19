package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.RiskClass;
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
 * This class is where the risk class is decided, from a rating and two configured
 * thresholds. None of that reaches the model: the BPMN asks whether the loan may be
 * approved, and the aggregate answers from what {@link #assessRisk(Aggregate)} wrote.
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
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param customerName  Who is asking. Personal data, which never reaches the BPMS.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final String customerName,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .customerName(customerName)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates the request and derives the risk class from it.
   *
   * <p>
   * The rating and the risk class are the data model of this use case, and the BPMN knows
   * neither: the model asks the two questions {@link Aggregate#isApprovableWithoutReview()}
   * and {@link Aggregate#isWorthAManualReview()}, and this method decides what the answers
   * are. Both thresholds live in the module's configuration, so moving one is a
   * configuration change instead of a new process version.
   * </p>
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessRisk(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);
    loanApproval.setRiskClass(riskClassOf(rating));

    log.info(
        "Loan approval '{}' has a rating of {}, which is risk class {}",
        loanApproval.getLoanRequestId(),
        rating,
        loanApproval.getRiskClass());

  }

  /**
   * The loan is approved without anybody looking at it, which is the branch taken for risk
   * class {@link RiskClass#LOW}.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void approveLoan(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("approved");

    log.info("Loan approval '{}' was approved", loanApproval.getLoanRequestId());

  }

  /**
   * A person has to look at the request, which is the branch taken for risk class
   * {@link RiskClass#MEDIUM}.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void requestManualReview(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("under-review");

    log.info(
        "Loan approval '{}' goes to a manual review",
        loanApproval.getLoanRequestId());

  }

  /**
   * The loan is rejected, which is the default flow: neither question was answered with
   * yes.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void rejectLoan(
      final Aggregate loanApproval) {

    loanApproval.setOutcome("rejected");

    log.info("Loan approval '{}' was rejected", loanApproval.getLoanRequestId());

  }

  /**
   * What a rating counts as. The values of this enum are the application's business, and
   * the BPMN knows none of them, which is why replacing them is a change to Java alone.
   *
   * @param rating The credit rating.
   * @return The risk class.
   */
  private RiskClass riskClassOf(
      final int rating) {

    if (rating >= properties.getLowRiskRating()) {
      return RiskClass.LOW;
    }
    if (rating >= properties.getMediumRiskRating()) {
      return RiskClass.MEDIUM;
    }
    return RiskClass.HIGH;

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

}
