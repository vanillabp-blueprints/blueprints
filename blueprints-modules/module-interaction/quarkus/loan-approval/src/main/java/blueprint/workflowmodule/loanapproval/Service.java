package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import blueprint.api.RiskAssessments;
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
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * No method here throws a {@code TaskException}. One which does, so the process can react,
 * needs {@code @Transactional(dontRollbackOn = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for
 * rollback.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Transactional
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * The other workflow module, seen through the interface it offers. This is the only thing
   * this use case knows about it: no aggregate, no process, no class of that module.
   */
  @Inject
  RiskAssessments riskAssessments;

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
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * Asks the risk assessment for its verdict. The call returns immediately: what comes back
   * is an event, later, and the process waits for it in the meantime.
   *
   * <p>
   * This is the whole interaction between the two use cases, and it is deliberately an
   * ordinary method call against an interface. Nothing here knows that the other module runs
   * a process of its own, and nothing there knows that a process is waiting here.
   * </p>
   *
   * @param loanApproval The loan approval to have assessed.
   */
  public void requestRiskAssessment(
      final Aggregate loanApproval) {

    riskAssessments.request(
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount());

    log.info(
        "Risk assessment requested for loan approval '{}'",
        loanApproval.getLoanRequestId());

  }

  /**
   * The risk assessment answered. The value goes onto the aggregate and the process is told
   * that what it waits for has happened.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param score         What the other module worked out.
   */
  public void riskAssessed(
      final String loanRequestId,
      final int score) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalStateException(
            "Risk assessment answered for unknown loan approval '"
                + loanRequestId
                + "'"));

    loanApproval.setRiskScore(score);

    workflow.riskAssessed(loanApproval);

    log.info(
        "Loan approval '{}' knows its risk score: {}",
        loanRequestId,
        score);

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
