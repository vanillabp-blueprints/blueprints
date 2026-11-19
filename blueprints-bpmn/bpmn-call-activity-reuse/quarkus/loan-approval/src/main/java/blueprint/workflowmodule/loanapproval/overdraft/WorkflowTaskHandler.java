package blueprint.workflowmodule.loanapproval.overdraft;

import blueprint.workflowmodule.loanapproval.overdraft.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the overdraft request process tells the application. The task names are the same as
 * in the loan approval where the step is the same. VanillaBP wires a task by its process, so
 * the two never mix up.
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "overdraft_request"))
public class WorkflowTaskHandler {

  @Inject
  Service overdraftRequest;

  /**
   * Called by VanillaBP when the service task in front of the collapsed pool is reached.
   *
   * @param overdraft The workflow's aggregate.
   */
  @WorkflowTask
  public void requestCreditCheck(
      final Aggregate overdraft) {

    overdraftRequest.requestCreditCheck(overdraft);

  }

  /**
   * Called by VanillaBP when the service task behind the message event is reached.
   *
   * @param overdraft The workflow's aggregate.
   */
  @WorkflowTask
  public void setLimit(
      final Aggregate overdraft) {

    overdraftRequest.setLimit(overdraft);

  }

}
