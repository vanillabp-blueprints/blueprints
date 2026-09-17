package blueprint.workflowmodule.loanapproval;


import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * A driving adapter, the same kind of thing as {@link ApiController}: something outside
 * triggers, and the trigger is translated into a call to {@link Service}. That the caller
 * is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * Every method here runs on a thread of the BPMS, where no person is acting. The changes
 * they make therefore end up in the trail under the name the auditing uses for the
 * process itself.
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
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * Called by VanillaBP for the user task of the same name. The task is not completed by
   * returning from this method - only the application answering it later completes it,
   * and it needs the {@code @TaskId} kept here to do so.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The BPMS-side id of this user task.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanApproval,
      @TaskId final String taskId) {

    service.riskAssessmentOpened(loanApproval, taskId);

  }

  /**
   * Called by VanillaBP when the completed user task was followed by the service task of
   * the same name. It is the change which makes the state of the decision a past state.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void payOutLoan(
      final Aggregate loanApproval) {

    service.payOutLoan(loanApproval);

  }

}
