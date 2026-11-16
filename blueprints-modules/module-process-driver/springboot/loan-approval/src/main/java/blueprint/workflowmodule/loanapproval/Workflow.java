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
 * {@link Service} calls in, naming what happened in business terms, and this class translates
 * that into what the process needs: starting a workflow and completing a user task. {@link ProcessService} is injected here and nowhere else.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Wiring-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * A loan was requested, and the credit rating agreed. VanillaBP persists the aggregate and
   * starts the process in the same transaction.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    bpms.startWorkflow(loanApproval);

  }

  /**
   * The risk was assessed, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task "Assess risk".
   */
  public void riskAssessed(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

  /**
   * The signed contract was checked, which completes the user task.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task "Check contract".
   */
  public void contractChecked(
      final Aggregate loanApproval,
      final String taskId) {

    bpms.completeUserTask(loanApproval, taskId);

  }

}
