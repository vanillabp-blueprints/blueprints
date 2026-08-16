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
 * The only class using {@link ProcessService}. Everything the application has to tell the
 * BPMN is a method here, named after the business event rather than after the BPMN element
 * it happens to move.
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
   * The partner approved, so the user task waiting for that answer is completed and this
   * branch moves on to the join. The other branch is untouched by it.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the open task, as reported when it was delivered.
   */
  public void partnerApproved(
      final Aggregate loanApproval,
      final String taskId) {

    processService.completeUserTask(loanApproval, taskId);

  }

}
