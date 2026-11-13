package blueprint.workflowmodule.loanapproval;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.cockpit.workflow.PrefilledWorkflowDetails;
import io.vanillabp.spi.cockpit.workflow.WorkflowDetails;
import io.vanillabp.spi.cockpit.workflow.WorkflowDetailsProvider;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. The
 * {@code @WorkflowTask} method is called when the user task is created. The
 * {@code @WorkflowDetailsProvider} method is called whenever the cockpit adapter reports the
 * workflow, and says what the Business Cockpit shows about the case.
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

  /** The key of the applicant in the details of a case. A column of the case list reads it. */
  public static final String APPLICANT = "applicant";

  /** The key of the outcome in the details of a case. A column of the case list reads it. */
  public static final String OUTCOME = "outcome";

  @Autowired
  private Service loanApproval;

  /**
   * Called by VanillaBP for the user task of the same name when it is created. Returning does
   * not complete the task. It stays open until the loan request is approved, or until the case
   * is cancelled.
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

  /**
   * Says what the Business Cockpit shows about a case, beyond what the BPMN says. The cockpit
   * adapter calls it for every workflow of this process, whenever it reports one: when the
   * workflow starts, when it changes and when it ends.
   *
   * <p>
   * The details are business data for the columns of the case list: the applicant, and once the
   * case ended, its outcome. The outcome tells a cancelled case from an approved one. The cockpit
   * cannot tell them apart by itself, because both end at an end event of the process, see the
   * README.
   * </p>
   *
   * <p>
   * The method runs in the transaction of the event, with the aggregate as the event left it.
   * It only reads the aggregate. The cockpit never gets the aggregate itself, only what this
   * method hands over.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param details     What the cockpit adapter already knows about the workflow.
   * @return The details to report.
   */
  @WorkflowDetailsProvider
  public WorkflowDetails workflowDetails(
      final Aggregate loanRequest,
      final PrefilledWorkflowDetails details) {

    final var columns = new HashMap<String, Object>();
    columns.put(APPLICANT, loanRequest.getApplicant());
    if (loanRequest.getOutcome() != null) {
      columns.put(OUTCOME, loanRequest.getOutcome().name());
    }
    details.setDetails(columns);

    return details;

  }

}
