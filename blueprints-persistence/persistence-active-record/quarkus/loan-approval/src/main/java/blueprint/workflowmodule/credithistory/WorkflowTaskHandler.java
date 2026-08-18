package blueprint.workflowmodule.credithistory;

import blueprint.workflowmodule.credithistory.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring of this use
 * case.
 *
 * <p>
 * As in the loan approval there is no {@code @Transactional} here. VanillaBP loads the
 * aggregate, runs the method and saves the aggregate in one transaction it owns - and for this
 * aggregate that transaction contains a MongoDB transaction, because Panache enlists its
 * session in it.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "credit_history"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The aggregate
   * is loaded before and saved after the call, so the business code only has to change it.
   *
   * @param creditHistory The workflow's aggregate.
   */
  @WorkflowTask
  public void collectHistoryEntries(
      final Aggregate creditHistory) {

    service.collectHistoryEntries(creditHistory);

  }

}
