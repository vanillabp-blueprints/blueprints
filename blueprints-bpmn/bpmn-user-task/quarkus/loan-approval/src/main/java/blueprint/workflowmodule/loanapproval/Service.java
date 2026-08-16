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
 * what happened, {@code riskAssessed} rather than "complete the user task", and that class
 * decides what this means for the BPMN. The other direction runs through
 * {@link WorkflowTaskHandler}, which calls the methods below when the process reaches a
 * task.
 * </p>
 *
 * <p>
 * A user task splits its handling in two, and both halves are here: the process reports
 * that somebody has to assess the risk ({@link #riskAssessmentOpened}), and much later the
 * application reports the answer ({@link #assessRisk}). Nothing keeps the two together but
 * the task id on the workflow aggregate.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow and answering a user task have to run in a transaction. It is
 * deliberately absent from the methods a task handler calls: VanillaBP already runs a task
 * in a transaction it owns, and a transaction declared here would break the guarantees that
 * come with it. VanillaBP sees such a transaction and fails the task naming it, so the
 * mistake shows up rather than costing data.
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
   * Rates a loan request, which is what the service task ahead of the risk assessment
   * triggers.
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
   * Somebody has to assess the risk: the process created the user task and reports its id.
   * Keeping that id is the entire job here, because it is the only way back to this task.
   *
   * <p>
   * A real application would do here what its users need - send an email, create an entry
   * in its own task list, hand the task to the
   * <a href="https://github.com/vanillabp/business-cockpit">VanillaBP Business Cockpit</a>.
   * This blueprint logs the URLs continuing the process, so it can be operated in a
   * browser.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void riskAssessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setRiskAssessmentTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a risk assessment. Continue with one of:"
            + "\n  Acceptable    -> http://localhost:8080/api/loan-approval/{}/assess-risk/{}?riskIsAcceptable=true"
            + "\n  Too risky     -> http://localhost:8080/api/loan-approval/{}/assess-risk/{}?riskIsAcceptable=false"
            + "\n  Withdraw      -> http://localhost:8080/api/loan-approval/{}/withdraw/{}",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(), taskId,
        loanApproval.getLoanRequestId(), taskId,
        loanApproval.getLoanRequestId(), taskId);

  }

  /**
   * The risk assessment is gone without having been answered: the workflow canceled the
   * user task. Whatever was set up when it was created is torn down here, and the stored
   * id is dropped because it does not lead anywhere any more.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void riskAssessmentClosed(
      final Aggregate loanApproval) {

    loanApproval.setRiskAssessmentTaskId(null);

    log.info(
        "The risk assessment of loan approval '{}' was canceled",
        loanApproval.getLoanRequestId());

  }

  /**
   * The risk was assessed. This is the answer the process waits for, and it arrives
   * through the API rather than through the BPMS.
   *
   * @param loanRequestId    The natural id of the loan request.
   * @param taskId           The id of the user task being answered.
   * @param riskIsAcceptable What the assessment concluded.
   */
  @Transactional
  public void assessRisk(
      final String loanRequestId,
      final String taskId,
      final boolean riskIsAcceptable) {

    final var loanApproval = openRiskAssessment(loanRequestId, taskId);

    loanApproval.setRiskAcceptable(riskIsAcceptable);

    workflow.riskAssessed(loanApproval, taskId);

    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setRiskAssessmentTaskId(null);

    log.info(
        "Risk of loan approval '{}' was assessed as {}",
        loanRequestId,
        riskIsAcceptable ? "acceptable" : "too high");

  }

  /**
   * The customer withdraws the request while the risk assessment is still open. The task
   * is canceled by BPMN error, so the process reacts to it instead of ending as if the
   * assessment had happened.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task being canceled.
   */
  @Transactional
  public void withdrawLoanRequest(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = openRiskAssessment(loanRequestId, taskId);

    workflow.loanRequestWithdrawn(loanApproval, taskId);

    log.info("Loan approval '{}' was withdrawn by the customer", loanRequestId);

  }

  /**
   * Tells the customer how their request ended, which is what the service task behind the
   * user task triggers.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void informCustomer(
      final Aggregate loanApproval) {

    loanApproval.setCustomerInformed(true);

    log.info(
        "The customer of loan approval '{}' was informed that the risk is {}",
        loanApproval.getLoanRequestId(),
        Boolean.TRUE.equals(loanApproval.getRiskAcceptable()) ? "acceptable" : "too high");

  }

  /**
   * Notes that the request was withdrawn, which is what the service task on the error path
   * triggers.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void noteWithdrawal(
      final Aggregate loanApproval) {

    loanApproval.setWithdrawn(true);

    log.info(
        "Loan approval '{}' ended as withdrawn",
        loanApproval.getLoanRequestId());

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

  /**
   * The loan approval whose risk assessment is the given task, refusing anything else. A
   * task id is a URL somebody keeps, so it outlives the task it points at: the same link
   * opened twice, or after the request was withdrawn, has to be rejected here rather than
   * being sent to the BPMS.
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

    if (!taskId.equals(loanApproval.getRiskAssessmentTaskId())) {

      throw new IllegalStateException("The risk assessment '"
          + taskId
          + "' of loan approval '"
          + loanRequestId
          + "' is not open any more");

    }

    return loanApproval;

  }

}
