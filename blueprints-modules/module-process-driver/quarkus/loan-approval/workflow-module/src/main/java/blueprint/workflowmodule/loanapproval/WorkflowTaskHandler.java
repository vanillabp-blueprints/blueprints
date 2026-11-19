package blueprint.workflowmodule.loanapproval;

import java.util.Map;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.cockpit.usertask.PrefilledUserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetailsProvider;
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
 * A {@code @WorkflowTask} method contains no business logic. It turns what the BPMS delivers
 * into a call to {@link Service}. The service task which talks to the e-signature service is one
 * line here, because the port is used in the business code, not in the handler.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  /** The key of the amount in the details of a user task, as the cockpit receives them. */
  public static final String AMOUNT = "amount";

  /** The key of the credit score in the details of a user task. */
  public static final String CREDIT_SCORE = "creditScore";

  @Inject
  Service loanApproval;

  /**
   * Called by VanillaBP when the user task "Assess risk" is created. Returning does not complete
   * the task. That happens later, through the API.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task. The cockpit knows the task by the same id.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanRequest,
      @TaskId final String taskId) {

    loanApproval.riskAssessmentOpened(loanRequest, taskId);

  }

  /**
   * Called by VanillaBP when the service task "Send contract for signing" is reached.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void sendContractForSigning(
      final Aggregate loanRequest) {

    loanApproval.sendContractForSigning(loanRequest);

  }

  /**
   * Called by VanillaBP when the user task "Check contract" is created, and again when the
   * workflow takes it away. {@code @TaskEvent} tells the two apart: {@code CREATED} when the task
   * opens, {@code CANCELED} when the message "Contract declined" ends it. Without the
   * {@code CANCELED} call the aggregate would keep the id of a task nobody can complete any more.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the user task.
   * @param event       Whether the task was created or taken away.
   */
  @WorkflowTask
  public void checkContract(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.contractCheckOpened(loanRequest, taskId);
      case CANCELED -> loanApproval.contractCheckClosed(loanRequest);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Says what the Business Cockpit shows about a user task, beyond what the BPMN says. The cockpit
   * adapter calls it whenever it reports a user task of this process. The tests read these
   * details back from the simulator, which plays the cockpit.
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the cockpit adapter already knows about the task, from the BPMN.
   * @return The details to report.
   */
  @UserTaskDetailsProvider(taskDefinition = UserTaskDetailsProvider.ALL)
  public UserTaskDetails userTaskDetails(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    details.setDetails(Map.of(
        AMOUNT, loanRequest.getAmount(),
        CREDIT_SCORE, loanRequest.getCreditScore()));

    return details;

  }

}
