package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the loan approval process: the outgoing half of the BPMN
 * wiring. {@link ProcessService} is injected here and nowhere else.
 *
 * <p>
 * Name the methods after the business event, never after the BPMN element. The model may
 * change, and the business code must not notice.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /**
   * Starting workflows and correlating messages happen through this bean. It is typed by the
   * workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * The name of the BPMN message the process waits for. The same string is the name of the
   * <code>bpmn:message</code> in the model, and there is no second place it is written down.
   */
  public static final String CREDIT_CHECKED = "CreditChecked";

  /**
   * A loan was requested. VanillaBP persists the aggregate and starts the process in the
   * same transaction.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    bpms.startWorkflow(loanApproval);

  }

  /**
   * The credit check reported its result, which is the message the workflow waits for.
   *
   * <p>
   * VanillaBP finds the workflow itself, by the aggregate's id. No correlation key is
   * modelled, and none is passed.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void creditChecked(
      final Aggregate loanApproval) {

    bpms.correlateMessage(loanApproval, CREDIT_CHECKED);

  }

}
