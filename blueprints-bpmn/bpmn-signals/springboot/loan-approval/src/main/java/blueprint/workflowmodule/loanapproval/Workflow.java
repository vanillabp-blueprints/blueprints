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
 * broadcasting a signal, completing a task. {@link ProcessService} is injected here and
 * nowhere else.
 * </p>
 *
 * <p>
 * Name the methods after the business event, never after the BPMN element, so
 * {@code interestRatePublished} and not {@code sendInterestRatePublishedSignal}. The model
 * may be remodelled, a signal may become a message, and the business code must not notice.
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
@Component
@Transactional
public class Workflow {

  /**
   * Starting workflows, broadcasting signals and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow - which for
   * a signal only decides the workflow module it is broadcast in, not who receives it.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  /**
   * The name of the BPMN signal the waiting loan approvals listen for. The same string is
   * the name of the <code>bpmn:signal</code> in the model, and there is no second place it
   * is written down.
   */
  public static final String INTEREST_RATE_PUBLISHED = "InterestRatePublished";

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
   * Today's interest rate was published, which is the news every waiting loan approval is
   * listening for.
   *
   * <p>
   * <b>No aggregate is passed, and there is no way to pass one.</b> A signal is a
   * broadcast: every element of this workflow module waiting for that name reacts, and no
   * caller can narrow that down to one workflow. Whoever needs to reach exactly one
   * workflow correlates a message instead.
   * </p>
   *
   * <p>
   * The broadcast is scoped to the workflow module of this {@code ProcessService} and
   * reaches every BPMS the module is deployed to, which is what keeps it complete while
   * workflows are being migrated from one BPMS to another. It does NOT reach other
   * workflow modules - an application wanting that sends the signal through the
   * {@code ProcessService} of each of them, because which modules are meant is a business
   * question.
   * </p>
   *
   * <p>
   * <b>The signal carries no data.</b> Not even the aggregate detour a message has: there
   * is no aggregate here to write to. What the receiving workflows need has to be readable
   * from the application's own data, which is why {@link Service} stores the rate before
   * broadcasting.
   * </p>
   */
  public void interestRatePublished() {

    processService.sendSignal(INTEREST_RATE_PUBLISHED);

  }

}
