package blueprint.workflowmodule.loanapproval;

import java.time.Instant;
import java.util.List;
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
 * Nothing here knows about load. The load driver calls the same methods a browser, a task
 * list or a credit bureau would call. That is the point: what is measured is the module as it
 * is, not a version of it made for measuring.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. A method
 * called by a workflow task already runs in the transaction VanillaBP opened for it and joins
 * it, but the annotation still shows that the method needs one.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
@Transactional
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private CreditBureau creditBureau;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   * @param documents     What the customer hands in with the request.
   */
  public void request(
      final String loanRequestId,
      final int amount,
      final String documents) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .documents(documents)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Asks the credit bureau for a score. The answer comes later, or not at all.
   *
   * @param loanApproval The loan approval to ask for.
   */
  public void askCreditBureau(
      final Aggregate loanApproval) {

    creditBureau.requestScore(
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount());

    log.info(
        "The credit bureau was asked about loan approval '{}'",
        loanApproval.getLoanRequestId());

  }

  /**
   * The credit bureau answers.
   *
   * <p>
   * An answer may arrive after the process stopped waiting for it. Then a clerk already has the
   * assessment, and the answer is dropped. Correlating it would fail, because no workflow waits
   * for this message any more.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param creditScore   The score.
   */
  public void creditScoreReceived(
      final String loanRequestId,
      final int creditScore) {

    final var loanApproval = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (loanApproval.getAssessmentTaskId() != null || loanApproval.getApproval() != null) {

      log.info(
          "The credit score of loan approval '{}' arrived too late and is dropped",
          loanRequestId);
      return;

    }

    loanApproval.setCreditScore(creditScore);

    workflow.creditScoreReceived(loanApproval);

    log.info(
        "Credit score of loan approval '{}' is {}",
        loanRequestId,
        creditScore);

  }

  /**
   * The assessment is open, and a clerk may pick it up.
   *
   * @param loanApproval The loan approval to assess.
   * @param taskId       The id of the user task.
   */
  public void assessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setAssessmentTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a clerk. Continue with one of:"
            + "\n  Approve -> http://localhost:8080/api/loan-approval/{}/assess/{}?approved=true"
            + "\n  Reject  -> http://localhost:8080/api/loan-approval/{}/assess/{}?approved=false",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(), taskId,
        loanApproval.getLoanRequestId(), taskId);

  }

  /**
   * The assessments which wait for a clerk, as a task list shows them.
   *
   * @return Up to 50 open assessments.
   */
  public List<Aggregate> openAssessments() {

    return loanApprovals.findTop50ByAssessmentTaskIdIsNotNullAndApprovalIsNull();

  }

  /**
   * A clerk decides. An approved loan waits for the next payout run.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param approved      The decision.
   */
  public void assess(
      final String loanRequestId,
      final String taskId,
      final boolean approved) {

    final var loanApproval = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (!taskId.equals(loanApproval.getAssessmentTaskId())) {

      throw new IllegalStateException("The assessment '"
          + taskId
          + "' of loan approval '"
          + loanRequestId
          + "' is not open any more");

    }

    loanApproval.setApproval(approved);
    if (approved) {
      loanApproval.setPayoutRunAt(nextPayoutRun());
    }

    workflow.loanAssessed(loanApproval, taskId);

    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setAssessmentTaskId(null);

    log.info(
        "Loan approval '{}' was {}",
        loanRequestId,
        approved ? "approved and waits for the payout run at "
            + loanApproval.getPayoutRunAt() : "rejected");

  }

  /**
   * Pays out the loan, which happens in the payout run. A real application would tell a
   * payment system here.
   *
   * @param loanApproval The loan approval to pay out.
   */
  public void payOut(
      final Aggregate loanApproval) {

    loanApproval.setPaidOut(Boolean.TRUE);

    log.info(
        "Loan approval '{}' was paid out",
        loanApproval.getLoanRequestId());

  }

  /**
   * Tells the customer that the loan was rejected.
   *
   * @param loanApproval The rejected loan approval.
   */
  public void informRejection(
      final Aggregate loanApproval) {

    loanApproval.setRejectionSent(Boolean.TRUE);

    log.info(
        "The customer of loan approval '{}' was told about the rejection",
        loanApproval.getLoanRequestId());

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

  /**
   * How many loan approvals have not reached an end yet.
   *
   * @return The number of unfinished loan approvals.
   */
  public long unfinished() {

    return loanApprovals.countByPaidOutIsNullAndRejectionSentIsNull();

  }

  /**
   * The start of the next payout run. The runs start at whole multiples of
   * {@code loan-approval.payout-run-every}, so every loan approved in between gets the same
   * moment.
   *
   * @return The start of the next payout run.
   */
  private Instant nextPayoutRun() {

    final var every = properties
        .getPayoutRunEvery()
        .toSeconds();
    final var now = Instant
        .now()
        .getEpochSecond();

    return Instant.ofEpochSecond((now / every + 1) * every);

  }

}
