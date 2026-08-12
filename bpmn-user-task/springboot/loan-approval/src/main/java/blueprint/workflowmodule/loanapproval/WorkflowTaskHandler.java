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
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That the
 * caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns.
 * A transaction declared by the application would take that guarantee away, which is why
 * such an annotation on this class or on a {@code @WorkflowTask} method fails the boot
 * naming the method.
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
   * Called by VanillaBP for the user task of the same name, which is what makes this the
   * one method of this class doing more than forwarding.
   *
   * <p>
   * A user task is not work the application performs, it is work somebody outside has to
   * do, so this method is a notification rather than an implementation. Returning from it
   * does not complete the task - only {@code ProcessService#completeUserTask} does, and
   * that call needs the {@code @TaskId} this method receives. Keeping it is therefore what
   * turns a notification into something the application can answer later.
   * </p>
   *
   * <p>
   * The same method is called a second time if the workflow takes the task away again,
   * with {@code @TaskEvent} telling the two apart: {@code CREATED} on delivery,
   * {@code CANCELED} when the task is canceled - by an interrupting boundary event, by the
   * workflow ending, or by the application itself calling {@code cancelUserTask}. A method
   * without a {@code @TaskEvent} parameter is never called for a cancellation, so
   * subscribing to it is a decision to make: without it the application would keep a task
   * id pointing at a task nobody can answer any more.
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
   * Called by VanillaBP when the completed user task was followed by the service task of
   * the same name.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void informCustomer(
      final Aggregate loanApproval) {

    service.informCustomer(loanApproval);

  }

  /**
   * Called by VanillaBP on the path the error boundary event of the user task leads to.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void noteWithdrawal(
      final Aggregate loanApproval) {

    service.noteWithdrawal(loanApproval);

  }

}
