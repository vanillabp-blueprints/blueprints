package blueprint.workflowmodule.riskassessment;

import blueprint.workflowmodule.riskassessment.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process of this module tells the application: the incoming half of its BPMN
 * wiring.
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "risk_assessment"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param assessment The workflow's aggregate.
   */
  @WorkflowTask
  public void evaluateRisk(
      final Aggregate assessment) {

    service.evaluateRisk(assessment);

  }

  /**
   * Called when the process reaches the task announcing the result. Publishing it is modelled
   * as a task of this process on purpose: the answer leaves the module at a point the model
   * shows, not somewhere in a callback.
   *
   * @param assessment The workflow's aggregate.
   */
  @WorkflowTask
  public void publishAssessment(
      final Aggregate assessment) {

    service.publishAssessment(assessment);

  }

}
