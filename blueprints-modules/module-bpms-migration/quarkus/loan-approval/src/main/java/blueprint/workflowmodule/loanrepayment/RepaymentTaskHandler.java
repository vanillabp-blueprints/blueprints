package blueprint.workflowmodule.loanrepayment;


import blueprint.workflowmodule.loanrepayment.model.Repayment;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the repayment process tells the application. One task, because this workflow is here
 * for its configuration rather than for its model.
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Repayment.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_repayment"))
public class RepaymentTaskHandler {

  @Inject
  RepaymentService service;

  /**
   * Called by VanillaBP when the service task of the same name is reached.
   *
   * @param repayment The workflow's aggregate.
   */
  @WorkflowTask
  public void bookInstalment(
      final Repayment repayment) {

    service.bookInstalment(repayment);

  }

}
