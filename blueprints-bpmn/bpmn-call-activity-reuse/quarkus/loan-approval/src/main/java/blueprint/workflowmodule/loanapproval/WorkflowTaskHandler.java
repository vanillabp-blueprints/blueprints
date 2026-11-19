package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the loan approval process tells the application: the incoming half of the BPMN
 * wiring.
 *
 * <p>
 * The class declares {@code loan_approval} and nothing else. The credit check is not a
 * secondary process of this aggregate, as a call activity would be. It is a process of its
 * own with a workflow service of its own, and this class only sees the service task which
 * asks for it.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP loads
 * the aggregate, runs the method and saves the aggregate in one transaction it owns.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Inject
  Service loanApproval;

  /**
   * Called by VanillaBP when the service task in front of the collapsed pool is reached. It
   * starts the credit check and returns at once. The process then waits at the message event.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void requestCreditCheck(
      final Aggregate loanRequest) {

    loanApproval.requestCreditCheck(loanRequest);

  }

  /**
   * Called by VanillaBP when the service task behind the message event is reached, which
   * proves that the result arrived.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void decideOnLoan(
      final Aggregate loanRequest) {

    loanApproval.decideOnLoan(loanRequest);

  }

}
