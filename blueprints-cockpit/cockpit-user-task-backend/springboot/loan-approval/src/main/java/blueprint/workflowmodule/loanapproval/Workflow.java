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
 * {@link Service} calls in, naming what happened in business terms ({@code riskAssessed}),
 * and this class translates that into what the process needs: starting a workflow or
 * completing a user task. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * <p>
 * Completing a user task is also what the Business Cockpit learns about. The cockpit adapter
 * sees the task end in the BPMS and reports it, together with the task which comes next. So a
 * form never tells the cockpit anything itself. It tells the workflow module, and the module
 * completes the task.
 * </p>
 *
 * <p>
 * All methods have to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the answer, and on a remote BPMS
 * the answer is only sent after that transaction committed. A rollback therefore leaves the
 * task open, rather than completing a task for work that was undone.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /**
   * Starting workflows and completing tasks happen through this bean. It is typed by the
   * workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * A loan was requested. VanillaBP persists the aggregate and starts the process in the same
   * transaction, so a workflow without its aggregate cannot happen.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    bpms.startWorkflow(loanApproval);

  }

  /**
   * The risk was assessed, so the user task waiting for that answer is completed.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open user task, as reported when it was created.
   */
  public void riskAssessed(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

  /**
   * The documents were checked, so the user task waiting for that answer is completed.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open user task, as reported when it was created.
   */
  public void documentsChecked(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

}
