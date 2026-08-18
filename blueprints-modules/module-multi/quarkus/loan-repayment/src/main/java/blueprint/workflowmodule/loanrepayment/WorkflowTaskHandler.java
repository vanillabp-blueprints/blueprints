package blueprint.workflowmodule.loanrepayment;

import blueprint.workflowmodule.loanrepayment.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring of the
 * second use case.
 *
 * <p>
 * The BPMN process id 'loan_repayment' is unique within this workflow module, and only
 * within it. Two modules may use the same id, which is what {@code name-clash-avoidance} is
 * about - see the README.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_repayment"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param repayment The workflow's aggregate.
   */
  @WorkflowTask
  public void scheduleInstallments(
      final Aggregate repayment) {

    service.scheduleInstallments(repayment);

  }

}
