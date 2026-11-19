package blueprint.workflowmodule.loanapproval.creditcheck;

import blueprint.workflowmodule.loanapproval.creditcheck.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the credit check process tells the application.
 *
 * <p>
 * An ordinary workflow service for an ordinary process. Nothing in it says that other
 * processes use this one. That is what reuse looks like here: the process does not change
 * when one more process starts to use it.
 * </p>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "credit_check"))
public class WorkflowTaskHandler {

  @Inject
  Service creditCheck;

  /**
   * Called by VanillaBP when the first service task is reached.
   *
   * @param check The workflow's aggregate.
   */
  @WorkflowTask
  public void askCreditAgency(
      final Aggregate check) {

    creditCheck.askCreditAgency(check);

  }

  /**
   * Called by VanillaBP when the service task behind the message event is reached. This is
   * where the result leaves the process.
   *
   * @param check The workflow's aggregate.
   */
  @WorkflowTask
  public void reportResult(
      final Aggregate check) {

    creditCheck.reportResult(check);

  }

}
