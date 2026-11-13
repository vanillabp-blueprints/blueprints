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
 * {@link Service} calls in, naming what happened in business terms ({@code reviewed}), and
 * this class translates that into what the process needs: starting a workflow, completing a user
 * task or correlating a message. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * <p>
 * The Business Cockpit learns about all of it from the BPMS. The cockpit adapter sees the task
 * end, or the workflow end, and reports it. So the case page never tells the cockpit anything
 * itself. It tells the workflow module, and the module tells the process.
 * </p>
 *
 * <p>
 * All methods have to run in a transaction, which is why the class carries
 * {@code @Transactional}: the aggregate is saved along with the answer, and on a remote BPMS
 * the answer is only sent after that transaction committed. A rollback therefore leaves the
 * process where it was, rather than withdrawing a loan request for work that was undone.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /**
   * The name of the message which starts the event subprocess "Withdraw loan request". It is the
   * name of the message in the BPMN file.
   */
  public static final String WITHDRAW_LOAN_REQUEST = "WithdrawLoanRequest";

  /**
   * Starting workflows, completing tasks and correlating messages happen through this bean. It
   * is typed by the workflow aggregate, so there is one per workflow.
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
   * process reaches its regular end.
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId      The id of the open user task, as reported when it was created.
   */
  public void reviewed(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

  }

  /**
   * Somebody withdrew the loan request. The message starts the interrupting event subprocess
   * "Withdraw loan request", which ends the open user task, records the withdrawal and ends the
   * workflow at its end event. The adapter finds the workflow by the id of the aggregate.
   *
   * @param loanRequest The workflow's aggregate.
   */
  public void withdrawalRequested(
      final Aggregate loanRequest) {

    bpms.correlateMessage(loanRequest, WITHDRAW_LOAN_REQUEST);

  }

}
