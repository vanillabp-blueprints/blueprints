package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link StartFormController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. The
 * {@code @WorkflowTask} method is called when the user task is created.
 * </p>
 *
 * <p>
 * The cockpit adapter reports the case and the user task without a line of code here. This
 * blueprint is about the header of the cockpit, so it adds no details to the reports.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here. VanillaBP loads the aggregate, runs the method and
 * saves the aggregate in one transaction it owns, and the method of {@link Service} called here
 * joins it.
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
  private Service loanApproval;

  /**
   * Called by VanillaBP for the user task of the same name when it is created. Returning does
   * not complete the task. It stays open until the loan request is approved.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of this user task. The cockpit knows the task by the same id.
   */
  @WorkflowTask
  public void reviewLoanRequest(
      final Aggregate loanRequest,
      @TaskId final String taskId) {

    loanApproval.reviewOpened(loanRequest, taskId);

  }

}
