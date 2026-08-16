package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
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
   * The risk assessment of version 1: a person decided, and the workflow recorded who.
   *
   * <p>
   * Business code of an older version stays until the last workflow using it has ended. It
   * is not a special case in the current code, it is a method of its own, and the version
   * range on the handler is what keeps the two apart.
   * </p>
   *
   * @param loanApproval The loan approval to assess.
   */
  public void assessRiskManually(
      final Aggregate loanApproval) {

    loanApproval.setCreditRating(rating(loanApproval));
    loanApproval.setAssessedBy(properties.getManualAssessor());

    log.info(
        "Loan approval '{}' was assessed by {}, the way version 1 of the process does it",
        loanApproval.getLoanRequestId(),
        loanApproval.getAssessedBy());

  }

  /**
   * The risk assessment of every version after the first one: a score, computed from the
   * credit rating.
   *
   * @param loanApproval The loan approval to assess.
   */
  public void assessRiskAutomatically(
      final Aggregate loanApproval) {

    final var rating = rating(loanApproval);

    loanApproval.setCreditRating(rating);
    loanApproval.setRiskScore(properties.getRatingScale() - rating);

    log.info(
        "Loan approval '{}' scored {}, the way the current version does it",
        loanApproval.getLoanRequestId(),
        loanApproval.getRiskScore());

  }

  private int rating(
      final Aggregate loanApproval) {

    return Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

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
