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
 * {@link Service} calls in, naming what happened in business terms
 * ({@code partnerApproved}), and this class translates that into whatever the process
 * needs: starting a workflow, telling the BPMS that the aggregate changed, completing a
 * task.
 * {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * <p>
 * Every method has to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the answer, and on a remote
 * BPMS the answer is only sent after that transaction committed. A rollback therefore
 * takes the completion with it and leaves the task open, rather than completing a task
 * for work that was undone.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  /**
   * Starting workflows, correlating messages and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Inject
  ProcessService<Aggregate> bpms;

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
   * The customer withdrew the request during the cool-off period. Nothing has to be
   * completed or correlated, because the workflow only waits for its timer. But the gateway
   * behind the timer decides on {@code withdrawn}, and the BPMS still holds the value of
   * the last time VanillaBP talked to it. {@code aggregateChanged} makes VanillaBP write the
   * shared values of the aggregate again.
   *
   * <p>
   * Without this call the gateway reads {@code false} and asks the partner, although the
   * customer withdrew. The push is sent once the transaction has committed, like every
   * other operation on the BPMS. It reads the values when it is sent, so sending it twice
   * does no harm.
   * </p>
   *
   * @param loanApproval The workflow's aggregate, already marked as withdrawn.
   * @see <a href=
   *      "https://github.com/vanillabp/spi-for-java/wiki/The-workflow-aggregate#tell-the-bpms-that-the-aggregate-changed">Tell
   *      the BPMS that the aggregate changed</a>
   */
  public void loanWithdrawn(
      final Aggregate loanApproval) {

    bpms.aggregateChanged(loanApproval);

  }

  /**
   * The partner answered in time, so the task waiting for that answer is completed and the
   * process moves on. If they do not answer, nothing calls this method - the timer on the
   * task ends the wait, and the application hears about it as a canceled task.
   *
   * <p>
   * VanillaBP finds the BPMS holding the task itself, by asking the configured adapters
   * in their order of priority. If none of them knows the id, a
   * {@code TaskNotFoundException} explains why that happens; if the task was completed
   * already, the call is a logged no-op rather than an error, which is what makes a
   * partner sending their answer twice harmless.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open task, as reported when it was delivered.
   */
  public void partnerApproved(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeTask(loanApproval, taskId);

  }

}
