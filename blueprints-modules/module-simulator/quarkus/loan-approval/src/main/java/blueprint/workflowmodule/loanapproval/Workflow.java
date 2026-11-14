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
 * that into what the process needs: starting a workflow, completing a user task, correlating a
 * message. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  /** The name of the message in the BPMN, the one the catch event "Contract signed" waits for. */
  public static final String CONTRACT_SIGNED = "ContractSigned";

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
   * The contract was prepared, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task "Prepare contract".
   */
  public void contractPrepared(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

  /**
   * The customer signed the contract. The workflow waits for exactly this message.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void contractSigned(
      final Aggregate loanApproval) {

    bpms.correlateMessage(loanApproval, CONTRACT_SIGNED);

  }

}
