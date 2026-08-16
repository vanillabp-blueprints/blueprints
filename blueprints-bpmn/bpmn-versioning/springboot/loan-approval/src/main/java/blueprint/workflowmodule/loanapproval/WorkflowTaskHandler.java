package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * The two methods below serve the SAME task definition, and which of them is called is
 * decided by the version of the process definition the workflow runs on. A workflow started
 * before the model was changed keeps the code it was started with, and it does so without a
 * flag on the aggregate or an if in the business code.
 * </p>
 *
 * <p>
 * The version is the one the BPMS counts per BPMN process id, not one the application
 * invents. A boundary may also be a version tag from the model. Ranges are written
 * {@code 1-3}, {@code >3} or {@code <v2.0}, and two methods serving one task definition may
 * not overlap: VanillaBP reports that while the application starts rather than when the
 * first task arrives.
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
   * The risk assessment as version 1 of the process meant it: a person looks at the request
   * and the workflow records who did.
   *
   * <p>
   * This method exists for the workflows which are still running on that version. It is not
   * dead code, and deleting it is what turns a running workflow into an incident at its next
   * task.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask(taskDefinition = "assessRisk", version = "1")
  public void assessRiskManually(
      final Aggregate loanApproval) {

    service.assessRiskManually(loanApproval);

  }

  /**
   * The risk assessment as every version after the first one means it: a score, computed
   * from the credit rating.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask(taskDefinition = "assessRisk", version = ">1")
  public void assessRiskAutomatically(
      final Aggregate loanApproval) {

    service.assessRiskAutomatically(loanApproval);

  }

}
