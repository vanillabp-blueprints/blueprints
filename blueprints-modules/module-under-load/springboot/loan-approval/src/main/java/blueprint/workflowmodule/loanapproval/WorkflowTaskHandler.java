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
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That the
 * caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * Every method here is one delivery of the BPMS, and under load each delivery holds a thread
 * and a database connection while it runs. That is why the load driver counts them.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP loads
 * the aggregate, runs the method and saves the aggregate in one transaction it owns.
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
   * Called when the process asks the credit bureau.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void requestCreditScore(
      final Aggregate loanRequest) {

    loanApproval.askCreditBureau(loanRequest);

  }

  /**
   * Called when the user task opens. The task stays open until a clerk decides, so the id is
   * kept on the aggregate.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task.
   */
  @WorkflowTask
  public void assessLoan(
      final Aggregate loanRequest,
      @TaskId final String taskId) {

    loanApproval.assessmentOpened(loanRequest, taskId);

  }

  /**
   * Called when the payout run starts, for every loan which waited for it.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void payOut(
      final Aggregate loanRequest) {

    loanApproval.payOut(loanRequest);

  }

  /**
   * Called when the clerk rejected the loan.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void informRejection(
      final Aggregate loanRequest) {

    loanApproval.informRejection(loanRequest);

  }

}
