package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * What the application tells the process: the outgoing half of the BPMN wiring.
 *
 * <p>
 * {@link Service} calls in, naming what happened in business terms, and this class translates
 * that into what the process needs: starting a workflow, completing a user task and correlating
 * a message. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  /**
   * The name of the BPMN message which tells the workflow that the customer declined to sign.
   * The same string is the name of the {@code bpmn:message} in the model.
   */
  public static final String CONTRACT_DECLINED = "ContractDeclined";

  @Inject
  ProcessService<Aggregate> bpms;

  /**
   * A loan was requested, and the credit rating agreed. VanillaBP persists the aggregate and
   * starts the process in the same transaction.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    bpms.startWorkflow(loanApproval);

  }

  /**
   * The risk was assessed, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task "Assess risk".
   */
  public void riskAssessed(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

  /**
   * The signed contract was checked, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task "Check contract".
   */
  public void contractChecked(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

  /**
   * The customer declined to sign the contract. The workflow takes the task "Check contract"
   * away and ends.
   *
   * <p>
   * Only the name of the message reaches the BPMS. That the contract was declined is written to
   * the aggregate, in the same transaction. The decline may come before the workflow waits in
   * "Check contract", so the module gives its messages a time-to-live, see
   * {@code loan-approval.yaml}.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void contractDeclined(
      final Aggregate loanApproval) {

    bpms.correlateMessage(loanApproval, CONTRACT_DECLINED);

  }

}
