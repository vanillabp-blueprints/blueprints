package blueprint.workflowmodule.ratewatch;

import blueprint.workflowmodule.ratewatch.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the rate watch process tells the application.
 *
 * <p>
 * One task, behind the signal catch event. Its being called at all is the assertion this
 * use case exists for: the loan approval broadcast the signal, this process caught it, and
 * no code connects the two.
 * </p>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "rate_watch"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the service task behind the signal event is reached.
   *
   * @param rateWatch The workflow's aggregate.
   */
  @WorkflowTask
  public void recordPublication(
      final Aggregate rateWatch) {

    service.rateNoticed(rateWatch);

  }

}
