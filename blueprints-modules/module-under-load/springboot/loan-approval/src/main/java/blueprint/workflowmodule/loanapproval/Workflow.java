package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the process: the outgoing half of the BPMN wiring.
 *
 * <p>
 * {@link Service} calls in, naming what happened in business terms ({@code loanRequested}),
 * and this class translates that into whatever the process needs: starting a workflow,
 * correlating a message, completing a task. {@link ProcessService} is injected here and
 * nowhere else.
 * </p>
 *
 * <p>
 * Each of the three calls below writes an entry into the outbox of VanillaBP, and the outbox
 * hands it to the BPMS after the commit. Under load, this is one of the queues the load driver
 * watches.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /**
   * The name of the BPMN message the process waits for. The same string is the name of
   * the <code>bpmn:message</code> in the model, and there is no second place it is
   * written down.
   */
  public static final String CREDIT_SCORE_RECEIVED = "CreditScoreReceived";

  /**
   * Starting workflows, correlating messages and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * A loan was requested. VanillaBP persists the aggregate and starts the process in the
   * same transaction, so a workflow without its aggregate cannot happen.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    bpms.startWorkflow(loanApproval);

  }

  /**
   * The credit bureau answered, which is the message the workflow waits for.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void creditScoreReceived(
      final Aggregate loanApproval) {

    bpms.correlateMessage(loanApproval, CREDIT_SCORE_RECEIVED);

  }

  /**
   * A clerk assessed the loan, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task.
   */
  public void loanAssessed(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

}
