package blueprint.workflowmodule.loanapproval;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.usertask.ApproveLoanTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.AssessRiskTaskData;
import blueprint.workflowmodule.loanapproval.model.usertask.CallCustomerTaskData;
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
 * Everything a user does is written into the aggregate together with who did it. The details
 * the Business Cockpit shows about a user task are read from there later: who caused a report,
 * who must not approve the loan, who worked on a task. Nothing else can tell. No BPMS records
 * the user, and by the time a report is built the request of that user is long gone.
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
   * An advisor requests a loan for a customer, and becomes the advisor of that customer.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   * @param customer      The name of the customer.
   * @param phone         The phone number of the customer.
   * @param user          The advisor who requests it.
   */
  public void request(
      final String loanRequestId,
      final int amount,
      final String customer,
      final String phone,
      final String user) {

    final var now = OffsetDateTime.now();
    final var loanRequest = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .customer(customer)
        .phone(phone)
        .requestedAt(now)
        .answerPromisedBy(now.plus(properties.getAnswerPromisedWithin()))
        .advisor(user)
        .lastActionBy(user)
        .build();

    workflow.loanRequested(loanRequest);

    log.info("Loan approval '{}' started by '{}'", loanRequestId, user);

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
   * The process opened the user task "Call customer". It belongs to the advisor of the
   * customer.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   */
  public void callCustomerOpened(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest.addTask(Task.opened(taskId, CallCustomerTaskData.empty()));

    log.info(
        "Loan approval '{}' waits for '{}' to call the customer. The form reads"
            + "\n  http://localhost:8080/api/loan-approval/{}/call-customer/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getAdvisor(),
        loanRequest.getLoanRequestId(),
        taskId);

  }

  /**
   * The process opened the user task "Assess risk". Any member of the risk team may take it.
   *
   * <p>
   * The risk team works from a shared mailbox. The module writes one mail there per task, and
   * the cockpit is told not to notify anybody about this task. The log line stands in for that
   * mail.
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
        "Mail to the risk team: loan approval '{}' waits for a risk assessment. The form reads"
            + "\n  http://localhost:8080/api/loan-approval/{}/assess-risk/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getLoanRequestId(),
        taskId);

  }

  /**
   * The process opened the user task "Approve loan".
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   */
  public void approvalOpened(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest.addTask(Task.opened(taskId, ApproveLoanTaskData.empty()));

    log.info(
        "Loan approval '{}' waits for an approval by somebody other than '{}'. The form reads"
            + "\n  http://localhost:8080/api/loan-approval/{}/approve-loan/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getRiskAssessedBy(),
        loanRequest.getLoanRequestId(),
        taskId);

  }

  /**
   * The workflow took a user task away before anybody completed it.
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
   * A user opened the form of an open task to work on it. The user is written into the task,
   * and the cockpit learns about it. From now on the user may read the task, even after their
   * authority is gone.
   *
   * <p>
   * This is written where the user really sees the task, when the form is delivered. A user
   * who never opened the form has nothing to read back later.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          Who opened the form.
   */
  public void formOpenedForWork(
      final String loanRequestId,
      final String taskId,
      final String user) {

    final var loanRequest = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new BcUnknownTaskException(loanRequestId, taskId));
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow(() -> new BcUnknownTaskException(loanRequestId, taskId));

    if (task.isOpen() && task.addEditor(user)) {
      workflow.taskDetailsChanged(loanRequest, taskId);
    }

  }

  /**
   * The advisor called and did not reach the customer. The task stays open and leaves the list
   * of open tasks until the next morning, when the advisor calls again.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The advisor who called.
   */
  public void customerNotReached(
      final String loanRequestId,
      final String taskId,
      final String user) {

    final var loanRequest = withOpenTask(loanRequestId, taskId, CallCustomerTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();
    final var nextCall = OffsetDateTime.now(properties.getZone())
        .toLocalDate()
        .plusDays(1)
        .atTime(properties.getCallAgainAt())
        .atZone(properties.getZone())
        .toOffsetDateTime();

    task.saveDraft(task
        .<CallCustomerTaskData>getData()
        .notReached(nextCall));
    task.addEditor(user);
    loanRequest.setLastActionBy(user);

    workflow.taskDetailsChanged(loanRequest, taskId);

    log.info(
        "'{}' did not reach the customer of loan approval '{}' and calls again at {}",
        user,
        loanRequestId,
        nextCall);

  }

  /**
   * The advisor reached the customer. The note goes into the aggregate, and the user task is
   * completed, both in this transaction.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param note          What the customer said.
   * @param user          The advisor who called.
   */
  public void customerCalled(
      final String loanRequestId,
      final String taskId,
      final String note,
      final String user) {

    final var loanRequest = withOpenTask(loanRequestId, taskId, CallCustomerTaskData.class);
    final var task = loanRequest
        .getTask(taskId)
        .orElseThrow();

    loanRequest.setCustomerNote(note);
    loanRequest.setLastActionBy(user);
    task.complete(task
        .<CallCustomerTaskData>getData()
        .reached(note), user);

    workflow.customerCalled(loanRequest, taskId);

    log.info("'{}' called the customer of loan approval '{}'", user, loanRequestId);

  }

  /**
   * The risk was assessed. The result goes into the aggregate together with who assessed it,
   * and the user task is completed, both in this transaction.
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

    loanRequest.setRiskAcceptable(riskAcceptable);
    loanRequest.setRiskComment(comment);
    loanRequest.setRiskAssessedBy(user);
    loanRequest.setLastActionBy(user);
    loanRequest
        .getTask(taskId)
        .orElseThrow()
        .complete(new AssessRiskTaskData(riskAcceptable, comment), user);

    workflow.riskAssessed(loanRequest, taskId);

    log.info(
        "Risk of loan approval '{}' was assessed as {} by '{}'",
        loanRequestId,
        riskAcceptable ? "acceptable" : "too high",
        user);

  }

  /**
   * The loan is approved or rejected. Not by the user who assessed the risk: the cockpit does
   * not offer the task to that user, and the module refuses it here as well.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the user task.
   * @param approved      Whether the loan is granted.
   * @param comment       Why.
   * @param user          Who completed the task.
   * @throws FourEyesViolation If the user assessed the risk of this loan request.
   */
  public void decide(
      final String loanRequestId,
      final String taskId,
      final Boolean approved,
      final String comment,
      final String user) {

    if (approved == null) {
      throw new IllegalArgumentException("An approval needs 'approved'");
    }

    final var loanRequest = withOpenTask(loanRequestId, taskId, ApproveLoanTaskData.class);
    if (assessedTheRisk(loanRequest, user)) {
      throw new FourEyesViolation(loanRequestId, user);
    }

    loanRequest.setLoanApproved(approved);
    loanRequest.setDecidedBy(user);
    loanRequest.setLastActionBy(user);
    loanRequest
        .getTask(taskId)
        .orElseThrow()
        .complete(new ApproveLoanTaskData(approved, comment), user);

    workflow.loanDecided(loanRequest, taskId);

    log.info(
        "Loan approval '{}' ended as {} by '{}'",
        loanRequestId,
        approved ? "approved" : "rejected",
        user);

  }

  /**
   * Whether the user assessed the risk of the loan request, and so must not approve it.
   *
   * @param loanRequest The loan request.
   * @param user        The user.
   * @return True if the four-eyes principle keeps the user away from the approval.
   */
  public boolean assessedTheRisk(
      final Aggregate loanRequest,
      final String user) {

    return user.equals(loanRequest.getRiskAssessedBy());

  }

  /**
   * The customer gets another advisor. If the advisor still has to call the customer, the
   * workflow takes that task away from the old advisor and opens it again for the new one.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param advisor       The new advisor.
   * @param user          Who made the change.
   * @throws IllegalStateException If nobody has to call the customer any more.
   */
  public void changeAdvisor(
      final String loanRequestId,
      final String advisor,
      final String user) {

    final var loanRequest = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));
    final var callIsOpen = loanRequest
        .getTasks()
        .values()
        .stream()
        .anyMatch(task -> task.isOpen() && task.holds(CallCustomerTaskData.class));
    if (!callIsOpen) {
      throw new IllegalStateException("Loan approval '"
          + loanRequestId
          + "' has no open call to hand over");
    }

    loanRequest.setAdvisor(advisor);
    loanRequest.setLastActionBy(user);

    workflow.advisorChanged(loanRequest);

    log.info(
        "'{}' handed the customer of loan approval '{}' over to '{}'",
        user,
        loanRequestId,
        advisor);

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

  /**
   * The user assessed the risk and now tries to approve the same loan. Spring Security answers
   * it with 403, like any other refused access.
   */
  public static class FourEyesViolation extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    FourEyesViolation(
        final String loanRequestId,
        final String user) {

      super("'"
          + user
          + "' assessed the risk of loan approval '"
          + loanRequestId
          + "' and must not approve it as well");

    }

  }

}
