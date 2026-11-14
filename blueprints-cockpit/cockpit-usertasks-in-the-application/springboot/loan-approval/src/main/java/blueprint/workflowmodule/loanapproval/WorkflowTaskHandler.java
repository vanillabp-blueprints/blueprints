package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.MultiInstanceElement;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * The two user tasks have the same kind of handler. On {@code CREATED} the business code
 * adds a row for the task to the aggregate. On {@code CANCELED} it marks that row as
 * canceled. Neither of them completes anything. A user task is completed later, by a user,
 * through the endpoints of its form.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  /** The id of the multi-instance user task in the model. */
  private static final String CHECK_DOCUMENT = "UserTask_CheckDocument";

  @Autowired
  private Service loanApproval;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanRequest) {

    loanApproval.assessCreditRating(loanRequest);

  }

  /**
   * Called by VanillaBP when the user task "Assess risk" is created, and again if the
   * workflow cancels it.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id the BPMS gave this user task.
   * @param event       Whether the task was created or canceled.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.riskAssessmentOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.taskCanceled(loanRequest, taskId);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Called by VanillaBP once for every instance of the multi-instance user task "Check
   * document", and again for every instance the workflow cancels.
   *
   * <p>
   * The document comes as {@code @MultiInstanceElement}, and it is needed only when the task
   * is created. A cancellation may come without it, because on some BPMS the element is gone
   * by then. That is why the cancellation finds the task by its id alone.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id the BPMS gave this instance of the user task.
   * @param event       Whether the task was created or canceled.
   * @param documentId  The document this instance is about.
   */
  @WorkflowTask
  public void checkDocument(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event,
      @MultiInstanceElement(CHECK_DOCUMENT) final String documentId) {

    switch (event) {
      case CREATED -> loanApproval.documentCheckOpened(loanRequest, taskId, documentId);
      case CANCELED -> loanApproval.taskCanceled(loanRequest, taskId);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Called by VanillaBP when the risk is acceptable and every document was checked.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void approveLoan(
      final Aggregate loanRequest) {

    loanApproval.approve(loanRequest);

  }

  /**
   * Called by VanillaBP when the risk was assessed as too high.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void rejectLoan(
      final Aggregate loanRequest) {

    loanApproval.reject(loanRequest);

  }

}
