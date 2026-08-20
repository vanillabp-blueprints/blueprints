package blueprint.workflowmodule.loanapproval;

import java.util.List;
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
 * expressed without a single word about processes and without a word about which BPMS runs
 * them.
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow, answering a user task and correlating a message all have to run in a
 * transaction. It is deliberately absent from the methods a task handler calls: VanillaBP
 * already runs a task in a transaction it owns.
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
   * The risk assessment was opened by the process. The id of the task is kept, because
   * answering it later needs it.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task just created.
   */
  public void riskAssessmentOpened(
      final Aggregate loanApproval,
      final String taskId) {

    loanApproval.setRiskAssessmentTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a risk assessment in {}. Continue with:"
            + "\n  Assess risk -> http://localhost:8080/api/loan-approval/{}/assess-risk/{}",
        loanApproval.getLoanRequestId(),
        bpmsHolding(loanApproval),
        loanApproval.getLoanRequestId(),
        taskId);

  }

  /**
   * The risk assessment is gone without having been answered: the workflow canceled the
   * user task, and the stored id does not lead anywhere any more.
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
   * The risk was assessed, which answers the user task the process waits at.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task being answered.
   */
  @Transactional
  public void assessRisk(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("unknown loan request '"
            + loanRequestId
            + "'"));

    loanApproval.setRiskAcceptable(Boolean.TRUE);

    workflow.riskAssessed(loanApproval, taskId);

    // the task is answered, so its id does not lead to an open task any more
    loanApproval.setRiskAssessmentTaskId(null);

    log.info(
        "Risk of loan approval '{}' was assessed. The workflow now waits for the signed"
            + " contract:"
            + "\n  Signed -> http://localhost:8080/api/loan-approval/{}/contract-signed?signedBy=Jane%20Doe",
        loanRequestId,
        loanRequestId);

  }

  /**
   * The contract came back signed. This is the message the workflow waits for, and it
   * arrives at the API rather than at the BPMS.
   *
   * <p>
   * The order of the two statements is the point: whatever the message carries is written
   * onto the aggregate FIRST, and only then is the message correlated. The BPMS learns the
   * name of the message and nothing else.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param signedBy      Who signed, taken from the message.
   */
  @Transactional
  public void contractSigned(
      final String loanRequestId,
      final String signedBy) {

    final var loanApproval = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("unknown loan request '"
            + loanRequestId
            + "'"));

    loanApproval.setContractSignedBy(signedBy);

    workflow.contractSigned(loanApproval);

    log.info(
        "The contract of loan approval '{}' was signed by {}",
        loanRequestId,
        signedBy);

  }

  /**
   * The last step of the process, and the proof that the message reached the workflow.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void payOut(
      final Aggregate loanApproval) {

    loanApproval.setPaidOut(Boolean.TRUE);

    log.info(
        "Loan approval '{}' was paid out",
        loanApproval.getLoanRequestId());

  }

  /**
   * Which BPMS holds a loan approval. Only this blueprint has a reason to ask, because
   * showing the routing is its subject.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The adapter id of the BPMS running this workflow.
   */
  @Transactional
  public Optional<String> bpmsHolding(
      final String loanRequestId) {

    return loanApprovals
        .findByIdOptional(loanRequestId)
        .map(this::bpmsHolding);

  }

  /**
   * @param loanApproval The workflow's aggregate.
   * @return The state of the loan approval, as far as the process has come.
   */
  @Transactional
  public Optional<Aggregate> getLoanApproval(
      final String loanApproval) {

    return loanApprovals.findByIdOptional(loanApproval);

  }

  /**
   * The adapter id out of the id of the process definition the workflow runs on. That id is
   * {@code <adapter-id>#<id of the BPMS>}, so its first part is the answer.
   *
   * @param loanApproval The workflow's aggregate.
   * @return The adapter id, or "unknown" if no BPMS knows this workflow any more.
   */
  private String bpmsHolding(
      final Aggregate loanApproval) {

    final List<String> definitions = workflow
        .definitionsOf(loanApproval)
        .stream()
        .map(definition -> definition
            .id()
            .split("#")[0])
        .distinct()
        .toList();
    return definitions.isEmpty()
        ? "unknown"
        : String.join(", ", definitions);

  }

}
