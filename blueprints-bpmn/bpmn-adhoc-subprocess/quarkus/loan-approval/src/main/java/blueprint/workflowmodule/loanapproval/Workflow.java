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
 * {@link Service} calls in, naming what happened in business terms ({@code checksSelected}),
 * and this class translates that into whatever the process needs. {@link ProcessService} is
 * injected here and nowhere else.
 * </p>
 *
 * <p>
 * There is nothing here about the ad-hoc subprocess, and that is worth noticing. Which of
 * its activities run is not an instruction the application sends, it is an attribute of the
 * workflow aggregate the model reads. So this class only completes the user task, and the
 * aggregate saved along with that completion carries the choice.
 * </p>
 *
 * <p>
 * Both methods have to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the answer, and on a remote
 * BPMS the answer is only sent after that transaction committed. A rollback therefore
 * takes the completion with it and leaves the task open, rather than completing a task for
 * work that was undone.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
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
   * A case worker picked the additional checks, so the user task waiting for that answer is
   * completed. The picked element ids are already on the aggregate, and VanillaBP shares the
   * aggregate with the BPMS while completing the task, so the process reaches the ad-hoc
   * subprocess with the list the model reads.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open user task, as reported when it was created.
   */
  public void checksSelected(
      final Aggregate loanApproval,
      final String taskId) {

    processService.completeUserTask(loanApproval, taskId);

  }

}
