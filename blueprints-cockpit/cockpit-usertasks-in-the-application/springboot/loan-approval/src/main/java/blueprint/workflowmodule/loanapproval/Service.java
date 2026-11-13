package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CheckDocumentTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import blueprint.workflowmodule.loanapproval.model.usertask.TaskData;
import io.vanillabp.cockpit.support.usertask.BcTaskNotOpenException;
import io.vanillabp.cockpit.support.usertask.BcUnknownTaskException;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * Every user task goes through the same three steps here. The process opens it, and the
 * service adds a {@link Task} to the aggregate. A user saves the form any number of times,
 * and the service changes that task and nothing else. A user completes the form, and in one
 * transaction the service checks that the task is still open, writes the result into the
 * aggregate, marks the task as completed and tells {@link Workflow}.
 * </p>
 *
 * <p>
 * The order of the last step matters. The result reaches the aggregate in the same
 * transaction which completes the task in the BPMS. If that transaction rolls back, neither
 * happened, and the task stays open with its last draft.
 * </p>
 *
 * <p>
 * {@code @Transactional} is on the class, so every method runs in a transaction. The
 * methods a task handler calls join the transaction VanillaBP runs the task in. The
 * annotation does no harm there, and it shows that they need a transaction as well: the
 * tasks of the aggregate and their data are loaded only when they are read. The methods
 * which only read say {@code readOnly = true}.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@Transactional
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
  public void request(
      final String loanRequestId,
      final int amount) {

    final var loanRequest = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();
    loanRequest
        .getDocumentIds()
        .addAll(properties.getRequiredDocuments());

    workflow.loanRequested(loanRequest);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates a loan request, which is what the service task ahead of the user tasks triggers.
   *
   * @param loanRequest The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanRequest) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanRequest.getAmount() / 100);

    loanRequest.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}",
        loanRequest.getLoanRequestId(),
        rating);

  }

  /**
   * The process opened the user task "Assess risk". The task becomes a row of the aggregate,
   * with an empty form.
   *
   * <p>
   * A real application would also tell the people who have to act, by mail or in its task
   * list. This blueprint logs the URL of the form.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   */
  public void riskAssessmentOpened(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest.addTask(Task.opened(taskId, AssessRiskTaskData.empty()));

    log.info(
        "Loan approval '{}' waits for a risk assessment. Its form reads"
            + "\n  http://localhost:8080/api/loan-approval/{}/assess-risk/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getLoanRequestId(),
        taskId);

  }

  /**
   * The process opened one instance of the user task "Check document". It becomes a row of
   * its own, which names the document.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   * @param documentId  The document this instance is about.
   */
  public void documentCheckOpened(
      final Aggregate loanRequest,
      final String taskId,
      final String documentId) {

    loanRequest.addTask(Task.opened(taskId, CheckDocumentTaskData.of(documentId)));

    log.info(
        "Loan approval '{}' waits for a check of the document '{}'. Its form reads"
            + "\n  http://localhost:8080/api/loan-approval/{}/check-document/{}",
        loanRequest.getLoanRequestId(),
        documentId,
        loanRequest.getLoanRequestId(),
        taskId);

  }

  /**
   * The workflow took a user task away before anybody completed it. The task is marked as
   * canceled. Its draft stays where it is and never reaches the aggregate.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task canceled.
   */
  public void taskCanceled(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest
        .getTask(taskId)
        .ifPresent(Task::cancel);

    log.info(
        "The user task '{}' of loan approval '{}' was canceled",
        taskId,
        loanRequest.getLoanRequestId());

  }

  /**
   * The loan request, if the given task belongs to it and is of the given kind. Used to read
   * a form, which works for a task that is open as well as for one that is done.
   *
   * <p>
   * Checking the kind reads the data of the task. So the tasks and the data of this one task
   * are loaded when the transaction ends, and the caller can show the form after it. The data
   * of the other tasks is not loaded.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param kind          The data of the kind of user task the caller expects.
   * @return The loan request.
   * @throws BcUnknownTaskException If there is no such task of that kind.
   */
  @Transactional(readOnly = true)
  public Aggregate withTask(
      final String loanRequestId,
      final String taskId,
      final Class<? extends TaskData> kind) {

    final var loanRequest = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new BcUnknownTaskException(loanRequestId, taskId));
    loanRequest
        .getTask(taskId)
        .filter(task -> task.holds(kind))
        .orElseThrow(() -> new BcUnknownTaskException(loanRequestId, taskId));
    return loanRequest;

  }

  /**
   * Keeps what the form "Assess risk" holds right now. Only the task changes. The aggregate
   * still says that nobody has assessed the risk yet, and that is the truth.
   *
   * @param loanRequestId  The natural id of the loan request.
   * @param taskId         The id of the user task.
   * @param riskAcceptable What the user has chosen so far, may be empty.
   * @param comment        What the user has written so far, may be empty.
   */
  public void saveRiskAssessmentDraft(
      final String loanRequestId,
      final String taskId,
      final Boolean riskAcceptable,
      final String comment) {

    final var loanRequest = withOpenTask(loanRequestId, taskId, AssessRiskTaskData.class);

    loanRequest
        .getTask(taskId)
        .orElseThrow()
        .saveDraft(new AssessRiskTaskData(riskAcceptable, comment));

    log.info("A draft of the risk assessment '{}' was saved", taskId);

  }

  /**
   * The risk was assessed. The result goes into the aggregate, and the user task is
   * completed, both in this transaction.
   *
   * @param loanRequestId  The natural id of the loan request.
   * @param taskId         The id of the user task.
   * @param riskAcceptable What the assessment concluded.
   * @param comment        Why.
   * @param user           Who completed the task.
   */
  public void assessRisk(
      final String loanRequestId,
      final String taskId,
      final Boolean riskAcceptable,
      final String comment,
      final String user) {

    if (riskAcceptable == null) {
      throw new IllegalArgumentException("A risk assessment needs 'riskAcceptable'");
    }

    final var loanRequest = withOpenTask(loanRequestId, taskId, AssessRiskTaskData.class);
    final var result = new AssessRiskTaskData(riskAcceptable, comment);

    loanRequest.setRiskAcceptable(result.riskAcceptable());
    loanRequest.setRiskComment(result.comment());
    loanRequest
        .getTask(taskId)
        .orElseThrow()
        .complete(result, user);

    workflow.riskAssessed(loanRequest, taskId);

    log.info(
        "Risk of loan approval '{}' was assessed as {} by '{}'",
        loanRequestId,
        riskAcceptable ? "acceptable" : "too high",
        user);

  }

  /**
   * Keeps what a form "Check document" holds right now. Only that task changes.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param valid         What the user has chosen so far, may be empty.
   * @param remark        What the user has written so far, may be empty.
   */
  public void saveDocumentCheckDraft(
      final String loanRequestId,
      final String taskId,
      final Boolean valid,
      final String remark) {

    final var task = withOpenTask(loanRequestId, taskId, CheckDocumentTaskData.class)
        .getTask(taskId)
        .orElseThrow();

    task.saveDraft(task
        .<CheckDocumentTaskData>getData()
        .with(valid, remark));

    log.info("A draft of the document check '{}' was saved", taskId);

  }

  /**
   * One document was checked. Its result goes into the aggregate, and its user task is
   * completed, both in this transaction.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param valid         Whether the document is in order.
   * @param remark        What the user noted.
   * @param user          Who completed the task.
   */
  public void checkDocument(
      final String loanRequestId,
      final String taskId,
      final Boolean valid,
      final String remark,
      final String user) {

    if (valid == null) {
      throw new IllegalArgumentException("A document check needs 'valid'");
    }

    final var loanRequest = withOpenTask(loanRequestId, taskId, CheckDocumentTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var result = task
        .<CheckDocumentTaskData>getData()
        .with(valid, remark);

    loanRequest
        .getDocumentChecks()
        .put(result.documentId(), result.valid());
    task.complete(result, user);

    workflow.documentChecked(loanRequest, taskId);

    log.info(
        "Document '{}' of loan approval '{}' was checked by '{}'",
        result.documentId(),
        loanRequestId,
        user);

  }

  /**
   * Approves the loan, which is what the service task behind both branches triggers.
   *
   * @param loanRequest The workflow's aggregate.
   */
  public void approve(
      final Aggregate loanRequest) {

    loanRequest.setLoanApproved(true);

    log.info("Loan approval '{}' ended as approved", loanRequest.getLoanRequestId());

  }

  /**
   * Rejects the loan, which is what the service task on the path of a too high risk triggers.
   *
   * @param loanRequest The workflow's aggregate.
   */
  public void reject(
      final Aggregate loanRequest) {

    loanRequest.setLoanApproved(false);

    log.info("Loan approval '{}' ended as rejected", loanRequest.getLoanRequestId());

  }

  /**
   * The state of a loan approval, as far as the process has come, as text: the aggregate
   * with every user task and its data.
   *
   * <p>
   * The text is written here, inside the transaction. The tasks and their data are loaded
   * when they are read, which is not possible any more once the transaction has ended.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval as text, if it exists.
   */
  @Transactional(readOnly = true)
  public Optional<String> describe(
      final String loanRequestId) {

    return loanApprovals
        .findById(loanRequestId)
        .map(Aggregate::toString);

  }

  /**
   * The loan request whose task of the given kind is still open, refusing anything else. A
   * task id reaches the application as a link somebody keeps. So the same form may be sent
   * twice, or after the workflow canceled the task. Both are refused here, before anything
   * changes.
   *
   * <p>
   * The kind is checked as well. Without it, a user allowed to check documents could send the
   * id of a risk assessment to the endpoint of a document check. The authority of an endpoint
   * only protects the tasks which really are of its kind.
   * </p>
   */
  private Aggregate withOpenTask(
      final String loanRequestId,
      final String taskId,
      final Class<? extends TaskData> kind) {

    final var loanRequest = withTask(loanRequestId, taskId, kind);

    if (!loanRequest
        .getTask(taskId)
        .orElseThrow()
        .isOpen()) {
      throw new BcTaskNotOpenException(loanRequestId, taskId);
    }

    return loanRequest;

  }

}
