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
@Component
@Transactional
public class Workflow {

  /**
   * Starting workflows, correlating messages and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  /**
   * The name of the BPMN message starting the process. The same string is the name of the
   * <code>bpmn:message</code> the start event references, and there is no second place it
   * is written down.
   */
  public static final String LOAN_REQUESTED = "LoanRequested";

  /**
   * A loan was requested. The workflow is started through its message start event rather
   * than by an explicit call, which is what a process modelled that way asks for.
   *
   * <p>
   * What changes for the application is one method name. The aggregate is persisted and
   * the workflow is started in the same transaction either way, so a workflow without its
   * aggregate still cannot happen, and the id of the aggregate is what the process is
   * addressed by afterwards.
   * </p>
   *
   * <p>
   * What the message carries is the aggregate's ID and nothing else - the same rule as for
   * every other message. Whatever the process needs belongs onto the aggregate before this
   * call.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    processService.startWorkflowByMessage(loanApproval, LOAN_REQUESTED);

  }

}
