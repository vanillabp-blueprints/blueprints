package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.Aggregate.Status;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan approval,
 * expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells {@link Workflow}
 * what happened, and that class decides what this means for the BPMN. The other direction runs
 * through {@link WorkflowTaskHandler}, which calls the methods below when the process reaches
 * the user task.
 * </p>
 *
 * <p>
 * The case page knows its case by the business id, which is the id of the aggregate. So every
 * method the page calls takes the id of the loan request.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. Starting a
 * workflow, completing a user task and correlating a message need one. The method a task handler
 * calls already runs in the transaction VanillaBP opened for the task and joins it, but the
 * annotation still shows that the method needs one.
 * </p>
 *
 * <p>
 * No method here throws a {@code TaskException}. One which does, so the process can react,
 * needs {@code @Transactional(noRollbackFor = TaskException.class)}: VanillaBP commits its
 * transaction for that exception, and a plain {@code @Transactional} would mark it for rollback.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@Transactional
public class Service {

  /** What became of a request to approve or withdraw a loan request. */
  public enum Change {
    /** The loan request is approved, or it is being withdrawn. */
    DONE,
    /** There is no loan request of that id. */
    NOT_FOUND,
    /** The loan request does not wait for its review (any more). Nothing changed. */
    NOT_POSSIBLE
  }

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param applicant     Who asks for the loan.
   * @param amount        The amount requested.
   */
  public void request(
      final String loanRequestId,
      final String applicant,
      final int amount) {

    final var loanRequest = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .applicant(applicant)
        .amount(amount)
        .status(Status.REQUESTED)
        .build();

    workflow.loanRequested(loanRequest);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Somebody has to review the loan request: the process created the user task and reports its
   * id. The cockpit adapter reports the same task to the Business Cockpit, where the case page
   * lists it.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   */
  public void reviewOpened(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest.setReviewTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a review. Its case page reads"
            + "\n  http://localhost:8080/loan-approval/api/cases/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getLoanRequestId());

  }

  /**
   * The loan request was reviewed and approved. This completes the user task, and the process
   * reaches its regular end.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return {@link Change#NOT_POSSIBLE} if the user task is not open.
   */
  public Change approve(
      final String loanRequestId) {

    final var found = loanApprovals.findById(loanRequestId);
    if (found.isEmpty()) {
      return Change.NOT_FOUND;
    }
    final var loanRequest = found.get();
    final var taskId = loanRequest.getReviewTaskId();
    if ((taskId == null) || (loanRequest.getStatus() != Status.REQUESTED)) {
      return Change.NOT_POSSIBLE;
    }

    loanRequest.setStatus(Status.APPROVED);
    workflow.reviewed(loanRequest, taskId);
    loanRequest.setReviewTaskId(null);

    log.info("Loan approval '{}' was approved", loanRequestId);

    return Change.DONE;

  }

  /**
   * Somebody withdrew the loan request on the case page: it is not needed any more. This is a
   * business decision, so the process handles it and ends the normal way. The message starts the
   * event subprocess "Withdraw loan request", and its service task calls
   * {@link #recordWithdrawal(Aggregate)}.
   *
   * <p>
   * The id of the review task is removed right away, because the event subprocess ends that
   * task. So a second request to withdraw or approve is refused, even on a BPMS which runs the
   * event subprocess a moment later.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @return {@link Change#NOT_POSSIBLE} if the loan request does not wait for its review.
   */
  public Change withdraw(
      final String loanRequestId) {

    final var found = loanApprovals.findById(loanRequestId);
    if (found.isEmpty()) {
      return Change.NOT_FOUND;
    }
    final var loanRequest = found.get();
    if ((loanRequest.getReviewTaskId() == null) || (loanRequest.getStatus() != Status.REQUESTED)) {
      return Change.NOT_POSSIBLE;
    }

    workflow.withdrawalRequested(loanRequest);
    loanRequest.setReviewTaskId(null);

    log.info("Loan approval '{}' is being withdrawn", loanRequestId);

    return Change.DONE;

  }

  /**
   * The process reached the service task "Record withdrawal" of the event subprocess. The status
   * says from now on that the loan request was withdrawn. The process then reaches the end event
   * of the event subprocess, a normal end. The report of that end reads the aggregate, so the
   * cockpit shows the status {@link Status#WITHDRAWN}.
   *
   * @param loanRequest The workflow's aggregate.
   */
  public void recordWithdrawal(
      final Aggregate loanRequest) {

    loanRequest.setStatus(Status.WITHDRAWN);

    log.info("Loan approval '{}' was withdrawn", loanRequest.getLoanRequestId());

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

}
