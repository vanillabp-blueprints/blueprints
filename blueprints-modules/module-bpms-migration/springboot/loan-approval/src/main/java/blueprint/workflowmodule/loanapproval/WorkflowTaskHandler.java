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
 * Nothing here knows which BPMS delivered the work. Both configured adapters call the same
 * methods of this class, which is why a migration is not a code change: the workflows of
 * the old BPMS and those of the new one are served by one implementation.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared by the application would roll back instead and throw away what the handler
 * wrote for the process to react to.
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
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the business code only has to
   * change it.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * Called by VanillaBP for the user task of the same name, the first of the two wait
   * states of this process.
   *
   * <p>
   * Returning from this method does not complete the task. Only
   * {@code ProcessService#completeUserTask} does, and it needs the {@code @TaskId} this
   * method receives, which is why the id is kept on the aggregate: the answer arrives
   * later, possibly after a restart with another BPMS configured, and VanillaBP then finds
   * the BPMS holding the task by itself.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The BPMS-side id of this user task.
   * @param event        Whether the task was created or canceled.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanApproval,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> service.riskAssessmentOpened(loanApproval, taskId);
      case CANCELED -> service.riskAssessmentClosed(loanApproval);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Called by VanillaBP when the service task behind the message event is reached, which is
   * what proves that the message arrived at the BPMS holding this workflow.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void payOut(
      final Aggregate loanApproval) {

    service.payOut(loanApproval);

  }

}
