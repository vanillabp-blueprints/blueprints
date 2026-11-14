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
 * {@link Service} calls in, naming what happened in business terms ({@code loanRequested}), and
 * this class translates that into what the process needs: starting a workflow or completing a
 * user task. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * <p>
 * The Business Cockpit learns about all of it from the BPMS. The cockpit adapter sees the
 * workflow start, and reports the new case. So the start form never tells the cockpit anything
 * itself. It tells the workflow module, and the module tells the process.
 * </p>
 *
 * <p>
 * All methods have to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the start, and on a remote BPMS the
 * start is only sent after that transaction committed.
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
   * @param loanRequest The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanRequest) {

    bpms.startWorkflow(loanRequest);

  }

  /**
   * The loan request was reviewed, so the user task waiting for that answer is completed. The
   * process reaches its end.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the open user task, as reported when it was created.
   */
  public void reviewed(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

  }

}
