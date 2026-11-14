package blueprint.workflowmodule.loanapproval;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
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
 * A loan request is started in two ways: by the start form in the header of the cockpit, and by
 * a person with a browser. Both end up in {@link #request(String, int)}.
 * </p>
 *
 * <p>
 * Every method runs in a transaction, so the class says {@code @Transactional}. Starting a
 * workflow and completing a user task need one. The method a task handler calls already runs in
 * the transaction VanillaBP opened for the task and joins it, but the annotation still shows that
 * the method needs one.
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

  /** What became of a request to approve a loan request. */
  public enum Change {
    /** The loan request is approved. */
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
   * A customer requests a loan. The module picks the id of the new loan request, because nobody
   * outside knows one yet.
   *
   * @param applicant Who asks for the loan.
   * @param amount    The amount requested.
   * @return The id of the new loan request.
   */
  public String request(
      final String applicant,
      final int amount) {

    final var loanRequest = Aggregate
        .builder()
        .loanRequestId(UUID
            .randomUUID()
            .toString())
        .applicant(applicant)
        .amount(amount)
        .build();

    workflow.loanRequested(loanRequest);

    log.info(
        "Loan approval '{}' started. Show it -> http://localhost:8080/loan-approval/api/loan-requests/{}",
        loanRequest.getLoanRequestId(),
        loanRequest.getLoanRequestId());

    return loanRequest.getLoanRequestId();

  }

  /**
   * Somebody has to review the loan request: the process created the user task and reports its
   * id. The cockpit adapter reports the same task to the Business Cockpit.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task just created.
   */
  public void reviewOpened(
      final Aggregate loanRequest,
      final String taskId) {

    loanRequest.setReviewTaskId(taskId);

    log.info(
        "Loan approval '{}' waits for a review. Approve it"
            + "\n  -> http://localhost:8080/loan-approval/api/loan-requests/{}/approve",
        loanRequest.getLoanRequestId(),
        loanRequest.getLoanRequestId());

  }

  /**
   * The loan request was reviewed and approved. This completes the user task, and the process
   * reaches its end.
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
    if (taskId == null) {
      return Change.NOT_POSSIBLE;
    }

    workflow.reviewed(loanRequest, taskId);
    loanRequest.setReviewTaskId(null);

    log.info("Loan approval '{}' was approved", loanRequestId);

    return Change.DONE;

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
