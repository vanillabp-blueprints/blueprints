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
 * needs: starting a workflow, correlating a message, completing a task.
 * {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * <p>
 * Both methods have to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the answer, and on a remote
 * BPMS the answer is only sent after that transaction committed. A rollback therefore
 * takes the completion with it and leaves the task open, rather than completing a task
 * for work that was undone.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  /**
   * The BPMN error raised when the partner refuses. The same string is the error code of
   * the error boundary event attached to the task, and there is no second place it is
   * written down.
   */
  public static final String PARTNER_REJECTED = "partner-rejected";

  /**
   * Starting workflows, correlating messages and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Inject
  ProcessService<Aggregate> processService;

  /**
   * A loan was requested. VanillaBP persists the aggregate and starts the process in the
   * same transaction, so a workflow without its aggregate cannot happen.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    processService.startWorkflow(loanApproval);

  }

  /**
   * The partner approved, so the task waiting for that answer is completed and the
   * process moves on.
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

    processService.completeTask(loanApproval, taskId);

  }

  /**
   * The partner refused. The task is canceled by BPMN error, so the workflow leaves it
   * through the error boundary event instead of the regular sequence flow.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open task.
   */
  public void partnerRejected(
      final Aggregate loanApproval,
      final String taskId) {

    processService.cancelTask(loanApproval, taskId, PARTNER_REJECTED);

  }

}
