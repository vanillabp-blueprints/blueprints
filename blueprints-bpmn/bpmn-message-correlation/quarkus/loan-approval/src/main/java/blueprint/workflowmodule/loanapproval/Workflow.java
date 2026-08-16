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
   * The name of the BPMN message the process waits for. The same string is the name of
   * the <code>bpmn:message</code> in the model, and there is no second place it is
   * written down.
   */
  public static final String CONTRACT_SIGNED = "ContractSigned";

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
   * The contract was signed, which is the message the workflow waits for.
   *
   * <p>
   * Only the NAME of the message reaches the BPMS. Everything the process needs to know
   * about it is on the aggregate, which VanillaBP saves in the same transaction - so a
   * rollback takes the correlation with it, and the BPMS never holds data the application
   * does not.
   * </p>
   *
   * <p>
   * VanillaBP finds the workflow itself, by the aggregate's id: no correlation key is
   * modelled, and none is passed. If no BPMS knows the workflow, a guiding
   * {@code WorkflowNotFoundException} says so rather than losing the message silently.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void contractSigned(
      final Aggregate loanApproval) {

    processService.correlateMessage(loanApproval, CONTRACT_SIGNED);

  }

}
