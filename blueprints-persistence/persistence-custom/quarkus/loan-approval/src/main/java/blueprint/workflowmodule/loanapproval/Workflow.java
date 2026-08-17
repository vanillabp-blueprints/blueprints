package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
 * Name the methods after the business event, never after the BPMN element, so
 * {@code loanRequested} and not {@code correlateLoanRequestedMessage}. The model may be
 * remodelled, a message may become a timer, and the business code must not notice.
 * </p>
 *
 * <p>
 * The incoming half, what the process tells the application, is
 * {@link WorkflowTaskHandler}. Keeping the two directions in two classes is what keeps the
 * dependencies acyclic: this class is used by {@link Service}, the other one uses it.
 * </p>
 *
 * <p>
 * Unlike in the other blueprints there is no transaction annotation on this class. An
 * annotation of the platform would open a transaction the store of this application is not part
 * of; the unit of work which counts is the one {@code Service} opens, and VanillaBP checks that
 * it is open before it starts a workflow.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
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

}
