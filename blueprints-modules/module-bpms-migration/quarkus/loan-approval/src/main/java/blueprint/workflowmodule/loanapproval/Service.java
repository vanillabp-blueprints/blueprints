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
  public Optional<Aggregate> get(
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
