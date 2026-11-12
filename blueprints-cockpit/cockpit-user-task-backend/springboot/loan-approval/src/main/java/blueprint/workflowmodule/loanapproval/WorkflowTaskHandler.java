package blueprint.workflowmodule.loanapproval;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.cockpit.usertask.PrefilledUserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetails;
import io.vanillabp.spi.cockpit.usertask.UserTaskDetailsProvider;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. The two
 * {@code @WorkflowTask} methods are called when a user task is created. The
 * {@code @UserTaskDetailsProvider} method is called whenever the cockpit adapter reports a user
 * task, and says what the Business Cockpit shows about it.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP loads
 * the aggregate, runs the method and saves the aggregate in one transaction it owns.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 * @see <a href=
 *      "https://github.com/vanillabp/business-cockpit/wiki/Reporting-workflows-and-user-tasks">Reporting
 *      workflows and user tasks</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  /** The key of the value in the details of a user task. A column of the task list reads it. */
  public static final String RISK_ASSESSMENT = "riskAssessment";

  @Autowired
  private Service loanApproval;

  /**
   * Called by VanillaBP for the user task of the same name when it is created. Returning does
   * not complete the task. The form does that later, through {@link UserTaskFormController}.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of this user task. The cockpit knows the task by the same id.
   */
  @WorkflowTask
  public void assessRisk(
      final Aggregate loanRequest,
      @TaskId final String taskId) {

    loanApproval.riskAssessmentOpened(loanRequest, taskId);

  }

  /**
   * Called by VanillaBP for the second user task when it is created.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of this user task.
   */
  @WorkflowTask
  public void checkDocuments(
      final Aggregate loanRequest,
      @TaskId final String taskId) {

    loanApproval.documentsCheckOpened(loanRequest, taskId);

  }

  /**
   * Says what the Business Cockpit shows about a user task, beyond what the BPMN says. The
   * cockpit adapter calls it for every user task of this process, whenever it reports one:
   * when the task is created, when it changes and when it ends.
   *
   * <p>
   * The details are business data for the columns of the task list. Here it is the risk
   * assessment. It is empty while "Assess risk" is open. It is there in the report which
   * completes that task, and in every report of "Check documents". A column of the module's
   * user interface shows the value under the key {@link #RISK_ASSESSMENT}.
   * </p>
   *
   * <p>
   * The method runs in the transaction of the event, with the aggregate as the event left it.
   * It only reads the aggregate. The cockpit never gets the aggregate itself, only what this
   * method hands over.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the cockpit adapter already knows about the task, from the BPMN.
   * @return The details to report.
   */
  @UserTaskDetailsProvider(taskDefinition = UserTaskDetailsProvider.ALL)
  public UserTaskDetails userTaskDetails(
      final Aggregate loanRequest,
      final PrefilledUserTaskDetails details) {

    final var columns = new HashMap<String, Object>();
    if (loanRequest.getRiskAssessment() != null) {
      columns.put(RISK_ASSESSMENT, loanRequest.getRiskAssessment());
    }
    details.setDetails(columns);

    return details;

  }

}
