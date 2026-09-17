package blueprint.workflowmodule.loanapproval;

import java.util.List;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.audit.AuditedAggregatePersistence;
import blueprint.workflowmodule.loanapproval.audit.ComplianceNotices;
import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan
 * approval, expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells
 * {@link Workflow} what happened, {@code riskAssessed} rather than "complete the user
 * task", and that class decides what this means for the BPMN. The other direction runs
 * through {@link WorkflowTaskHandler}, which calls the methods below when the process
 * reaches a task.
 * </p>
 *
 * <p>
 * Three of these methods write the loan approval, so three of them produce a revision:
 * the credit rating, the decision, and the payout after it. The decision is also the
 * moment the compliance archive has to hear about, and {@link ComplianceNotices} writes
 * that notice inside the same transaction.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * answering a user task has to run in a transaction. It is deliberately absent from the
 * methods a task handler calls: VanillaBP already runs a task in a transaction it owns,
 * and a transaction declared here would break the guarantees that come with it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service {

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  AuditedAggregatePersistence auditedLoanApprovals;

  @Inject
  ComplianceNotices complianceNotices;

  @Inject
  Workflow workflow;

  @Inject
  LoanApprovalProperties properties;

  /**
   * A customer requests a loan. The first state of the loan approval, and the first entry
   * of its trail.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   * @param requestedBy   Who asks for the loan.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount,
      final String requestedBy) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' was requested by {}", loanRequestId, requestedBy);

  }

  /**
   * Rates a loan request, which is what the service task ahead of the risk assessment
   * triggers. The first change the process itself makes, and the second revision.
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
   * Somebody has to assess the risk: the process created the user task and reports its
   * id. Keeping that id is the entire job here, because it is the only way back to this
   * task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void riskAssessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setAssessRiskTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a risk assessment. Continue with one of:"
            + "\n  Acceptable -> http://localhost:8080/api/loan-approval/{}/assess-risk/{}?riskIsAcceptable=true&decidedBy=paula"
            + "\n  Too risky  -> http://localhost:8080/api/loan-approval/{}/assess-risk/{}?riskIsAcceptable=false&decidedBy=paula",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(), taskId,
        loanApproval.getLoanRequestId(), taskId);

  }

  /**
   * The risk was assessed. This is the answer the process waits for, it arrives through
   * the API rather than through the BPMS, and it is the decision the archive has to hear
   * about.
   *
   * <p>
   * Everything below happens in one transaction: the decision is written, the notice is
   * written down, and the user task is completed. The notice therefore cannot survive a
   * rollback of the decision, and the state it names is the state the decision left.
   * </p>
   *
   * <p>
   * It is also the one change of this process a person makes. Who that is does not reach
   * this method as anything but a value it writes down: the trail learns it from the API,
   * which is where a request knows who sent it.
   * </p>
   *
   * @param loanRequestId    The natural id of the loan request.
   * @param taskId           The id of the user task being answered.
   * @param riskIsAcceptable What the assessment concluded.
   * @param decidedBy        Who assessed it.
   */
  @Transactional
  public void assessRisk(
      final String loanRequestId,
      final String taskId,
      final boolean riskIsAcceptable,
      final String decidedBy) {

    final var loanApproval = openRiskAssessment(loanRequestId, taskId);

    loanApproval.setRiskAcceptable(riskIsAcceptable);
    loanApproval.setDecidedBy(decidedBy);
    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setAssessRiskTaskId(null);

    complianceNotices.report(ComplianceNotices.RISK_ASSESSED, loanApproval);

    workflow.riskAssessed(loanApproval, taskId);

    log.info(
        "Risk of loan approval '{}' was assessed by {} as {}",
        loanRequestId,
        decidedBy,
        riskIsAcceptable ? "acceptable" : "too high");

  }

  /**
   * Pays the loan out, which is what the service task behind the risk assessment
   * triggers. It writes the loan approval once more, and from here on the state of the
   * decision and the state of today are two different things.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void payOutLoan(
      final Aggregate loanApproval) {

    loanApproval.setPaidOut(Boolean.TRUE.equals(loanApproval.getRiskAcceptable()));

    log.info(
        "Loan approval '{}' was {}",
        loanApproval.getLoanRequestId(),
        Boolean.TRUE.equals(loanApproval.getPaidOut()) ? "paid out" : "declined");

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

  /**
   * Every state a loan approval went through, with the person each change came from.
   * This is what an auditing is bought for, and it is a read of the audit tables rather
   * than of the loan approval itself.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The trail, oldest change first.
   */
  @Transactional
  public List<String> getTrail(
      final String loanRequestId) {

    return auditedLoanApprovals.trailOf(loanRequestId);

  }

  /**
   * The loan approval whose risk assessment is the given task, refusing anything else. A
   * task id is a URL somebody keeps, so it outlives the task it points at: the same link
   * opened twice has to be rejected here rather than being sent to the BPMS.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task expected to be open.
   * @return The loan approval.
   */
  private Aggregate openRiskAssessment(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (!taskId.equals(loanApproval.getAssessRiskTaskId())) {

      throw new IllegalStateException("The risk assessment '"
          + taskId
          + "' of loan approval '"
          + loanRequestId
          + "' is not open any more");

    }

    return loanApproval;

  }

}
