package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * Two of these methods belong to branches running at the same time, and neither of them
 * knows about the other. That is the normal case once a process holds more than one token,
 * and it is the reason the business code writes an entity per branch.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Autowired
  private Service service;

  /**
   * Called before the process splits, so this is the last task running on its own.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * The branch the application finishes. A user task waits for a person, so this method is
   * called when the task appears and the workflow stays there until the partner answered
   * through the API. {@code @TaskId} is what the application needs to answer it.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The BPMS-side id of this task.
   * @param event        Whether the task was delivered or canceled.
   */
  @WorkflowTask
  public void awaitPartnerApproval(
      final Aggregate loanApproval,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> service.partnerApprovalRequested(loanApproval, taskId);
      case CANCELED -> {
        // the workflow ended or was canceled while the task was open; nothing to keep
      }
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * The other branch asks the document service and waits for it. Nothing of this branch runs
   * before the application says the documents are there, which is what makes the order of
   * the two branches predictable instead of a matter of which job a worker picks first.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The BPMS-side id of this task.
   * @param event        Whether the task was delivered or canceled.
   */
  @WorkflowTask
  public void requestDocuments(
      final Aggregate loanApproval,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> service.documentsRequested(loanApproval, taskId);
      case CANCELED -> {
        // the workflow ended or was canceled while the task was open; nothing to keep
      }
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Reads the documents, in the transaction VanillaBP owns. It is called while the other
   * branch may be answered through the API, which is what this blueprint is about.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void recordDocuments(
      final Aggregate loanApproval) {

    service.recordDocuments(loanApproval);

  }

  /**
   * Called after the join, so a single token is left.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void informCustomer(
      final Aggregate loanApproval) {

    service.informCustomer(loanApproval);

  }

}
