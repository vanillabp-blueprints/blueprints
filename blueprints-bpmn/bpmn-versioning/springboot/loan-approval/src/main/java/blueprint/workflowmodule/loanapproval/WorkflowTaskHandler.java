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
 * The method below and the one in {@link WorkflowTaskHandlerOfVersion1} serve the SAME task
 * definition, and which of them is called is decided by the version of the process
 * definition the workflow runs on. A workflow started before the model was changed keeps the
 * code it was started with, and it does so without a flag on the aggregate or an if in the
 * business code.
 * </p>
 *
 * <p>
 * Here the range is written on the method. {@link WorkflowTaskHandlerOfVersion1} writes it
 * once on the class instead, for every method in it. Both ways can be mixed, as long as no two
 * methods of one task definition serve the same version.
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
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-task">Wire up a task</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Autowired
  private Service loanApproval;

  /**
   * The risk assessment as every version after the first one means it: a score, computed
   * from the credit rating.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask(taskDefinition = "assessRisk", version = ">1")
  public void assessRiskAutomatically(
      final Aggregate loanRequest) {

    loanApproval.assessRiskAutomatically(loanRequest);

  }

}
