package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * The three checks are the activities inside the ad-hoc subprocess, and they are the point
 * of this class: each of them is an ordinary {@code @WorkflowTask} method, written exactly
 * as it would be for a task on a sequence flow. Only some of them are called for a given
 * workflow, and which ones is nothing this class decides or learns. There is deliberately
 * no method for the subprocess itself - it is not a task of the application.
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
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Inject
  Service loanApproval;

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
   * Called by VanillaBP for the user task a big loan takes, which is where a person picks
   * the checks. Returning does not complete the task - the id this method keeps is what
   * lets the application answer it later.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId       The BPMS-side id of this user task.
   * @param event        Whether the task was created or canceled.
   */
  @WorkflowTask
  public void selectChecks(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.checkSelectionOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.checkSelectionClosed(loanRequest);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * One of the activities of the ad-hoc subprocess. It is called when the list on the
   * aggregate named this activity's element id, and not otherwise.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void checkIncome(
      final Aggregate loanRequest) {

    loanApproval.checkIncome(loanRequest);

  }

  /**
   * One of the activities of the ad-hoc subprocess.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void checkFraud(
      final Aggregate loanRequest) {

    loanApproval.checkFraud(loanRequest);

  }

  /**
   * One of the activities of the ad-hoc subprocess. The decision table never picks this
   * one, so it runs only where a case worker asked for it - and a method has to exist all
   * the same, because the wiring validation reads the model rather than the data.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void checkCollateral(
      final Aggregate loanRequest) {

    loanApproval.checkCollateral(loanRequest);

  }

  /**
   * Called by VanillaBP for the service task behind the ad-hoc subprocess, which the
   * workflow reaches once every activated check is done.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void informCustomer(
      final Aggregate loanRequest) {

    loanApproval.informCustomer(loanRequest);

  }

}
