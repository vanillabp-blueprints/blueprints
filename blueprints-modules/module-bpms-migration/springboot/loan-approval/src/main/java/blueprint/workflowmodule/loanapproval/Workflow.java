package blueprint.workflowmodule.loanapproval;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the process: the outgoing half of the BPMN wiring.
 *
 * <p>
 * Every method of this class is an operation on a workflow which may live in either of the
 * two BPMS this application is configured with, and not one of them names an adapter.
 * VanillaBP starts a workflow in the first adapter of the priority list and routes every
 * later operation to the adapter holding that workflow, so the code below is the same
 * whether the application runs against one BPMS or two.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-migration">BPMS
 *      migration</a>
 */
@Component
@Transactional
public class Workflow {

  /** The name of the message the process waits for, as spelled in the BPMN model. */
  private static final String CONTRACT_SIGNED = "ContractSigned";

  /**
   * Starting workflows, completing user tasks and correlating messages all happen through
   * this bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  /**
   * A loan was requested. The workflow is started in the FIRST adapter of the priority
   * list, without asking anybody: a new workflow has no history to look up.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void loanRequested(
      final Aggregate loanApproval) {

    processService.startWorkflow(loanApproval);

  }

  /**
   * The risk was assessed, which answers the user task.
   *
   * <p>
   * Here the election happens: VanillaBP asks the adapters of the priority list, in order,
   * whether they know this task, and the first one saying yes completes it. A workflow
   * started in the old BPMS is therefore answered there, however the priorities look
   * today.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the user task being answered.
   */
  public void riskAssessed(
      final Aggregate loanApproval,
      final String taskId) {

    processService.completeUserTask(loanApproval, taskId);

  }

  /**
   * The contract was signed, which is the message the workflow waits for. Elected the same
   * way as the user task above.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void contractSigned(
      final Aggregate loanApproval) {

    processService.correlateMessage(loanApproval, CONTRACT_SIGNED);

  }

  /**
   * Which BPMS holds this workflow, asked in the only way an application can ask: the id of
   * a process definition names the adapter it came from, as {@code <adapter-id>#<id of the
   * BPMS>}.
   *
   * <p>
   * This is a viewer call, so it elects the adapter like every other operation and reads
   * nothing but what that adapter answers. The blueprint uses it to make the routing
   * visible; an application normally has no reason to care.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @return The definitions this workflow runs on.
   */
  public List<ProcessDefinition> definitionsOf(
      final Aggregate loanApproval) {

    return processService.getProcessDefinitions(loanApproval, null);

  }

}
