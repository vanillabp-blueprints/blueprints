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
 * and this class translates that into what the process needs. {@link ProcessService} is
 * injected here and nowhere else.
 * </p>
 *
 * <p>
 * Both methods which complete a user task run in the transaction of {@link Service}. That
 * transaction also writes the result of the task into the aggregate. On a remote BPMS the
 * completion is sent only after it committed. So the result and the completion go together,
 * or neither of them does.
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
   * A loan was requested. VanillaBP persists the aggregate and starts the process in the
   * same transaction, so a workflow without its aggregate cannot happen.
   *
   * @param loanRequest The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanRequest) {

    bpms.startWorkflow(loanRequest);

  }

  /**
   * The risk was assessed, so the user task "Assess risk" is completed.
   *
   * @param loanRequest The workflow's aggregate, already holding the result.
   * @param taskId      The id of the open user task.
   */
  public void riskAssessed(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

  }

  /**
   * One document was checked, so its user task "Check document" is completed. The other
   * instances of the same task stay open.
   *
   * @param loanRequest The workflow's aggregate, already holding the result.
   * @param taskId      The id of the open user task.
   */
  public void documentChecked(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

  }

}
