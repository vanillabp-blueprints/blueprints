package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * The incoming half of the BPMN wiring for the workflows which still run on version 1 of the
 * process. The class names its range once, on {@code @BpmnProcess}, and every
 * {@code @WorkflowTask} method in it serves only that range, without saying so itself.
 *
 * <p>
 * This is worth it when a change of the model is so big that a whole set of handlers belongs
 * to the old model. They stay out of the way of the current code in {@link WorkflowTaskHandler},
 * and when no workflow runs on version 1 any more, this class is deleted as a whole. Two
 * classes may declare the same process for that reason, one per generation of the model, as
 * long as their ranges do not overlap.
 * </p>
 *
 * <p>
 * A method here which names a version of its own keeps it exactly as written, even if it
 * reaches outside the range of the class. The two are not intersected. And a method which
 * inherits a range is as restricted as a method which names one: a BPMS which reports no
 * version reaches neither of them.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Versions-of-a-process#a-range-for-a-whole-class">A
 *      range for a whole class</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval", version = "1"))
public class WorkflowTaskHandlerOfVersion1 {

  @Autowired
  private Service loanApproval;

  /**
   * The risk assessment as version 1 of the process meant it: a person looks at the request
   * and the workflow records who did. The method names no version, so it serves the range of
   * its class.
   *
   * <p>
   * This method exists for the workflows which are still running on that version. It is not
   * dead code, and deleting it is what turns a running workflow into an incident at its next
   * task.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask(taskDefinition = "assessRisk")
  public void assessRiskManually(
      final Aggregate loanRequest) {

    loanApproval.assessRiskManually(loanRequest);

  }

}
