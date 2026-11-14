package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.cockpit.BusinessCockpitService;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the process and the Business Cockpit: the outgoing half of the
 * wiring.
 *
 * <p>
 * {@link Service} calls in, naming what happened in business terms ({@code riskAssessed}),
 * and this class translates that into what the process needs. {@link ProcessService} is
 * injected here and nowhere else, and so is {@link BusinessCockpitService}.
 * </p>
 *
 * <p>
 * Every method runs in the transaction of {@link Service}. A completion is sent to a remote
 * BPMS only after that transaction committed, and a report to the cockpit goes through the
 * outbox. So nothing leaves the application for a change which was rolled back.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /** The message the model waits for while the customer is to be called back. */
  private static final String ADVISOR_CHANGED = "AdvisorChanged";

  /**
   * Starting workflows and completing tasks happen through this bean. It is typed by the
   * workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * Telling the cockpit about a change the BPMS never hears of, such as a follow-up date or a
   * new admitted user.
   */
  @Autowired
  private BusinessCockpitService<Aggregate> businessCockpit;

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
   * The customer has another advisor now. The open user task "Call customer" is canceled, and
   * the workflow enters it again. The new task belongs to the new advisor.
   *
   * <p>
   * A message and not a change of the open task, because the cockpit reads the assignee and
   * the candidates of a task only once, when it stores the task. A later report does not move
   * a task to other people. Ending the task and entering it again does.
   * </p>
   *
   * @param loanRequest The workflow's aggregate, already naming the new advisor.
   */
  public void advisorChanged(
      final Aggregate loanRequest) {

    bpms.correlateMessage(loanRequest, ADVISOR_CHANGED);

  }

  /**
   * Something the cockpit shows about one user task has changed, but the BPMS did not notice.
   * The cockpit adapter asks the details provider of the task again and reports what it says
   * now.
   *
   * @param loanRequest The workflow's aggregate, already changed.
   * @param taskId      The id of the user task.
   */
  public void taskDetailsChanged(
      final Aggregate loanRequest,
      final String taskId) {

    businessCockpit.aggregateChanged(loanRequest, taskId);

  }

  /**
   * The advisor reached the customer, so the user task "Call customer" is completed.
   *
   * @param loanRequest The workflow's aggregate, already holding the result.
   * @param taskId      The id of the open user task.
   */
  public void customerCalled(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

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
   * The loan was approved or rejected, so the user task "Approve loan" is completed.
   *
   * @param loanRequest The workflow's aggregate, already holding the result.
   * @param taskId      The id of the open user task.
   */
  public void loanDecided(
      final Aggregate loanRequest,
      final String taskId) {

    bpms.completeUserTask(loanRequest, taskId);

  }

}
