package blueprint.workflowmodule.loanapproval;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.process.WorkflowHistory;

/**
 * What the application tells the process, and what it asks the process: the outgoing half
 * of the BPMN wiring.
 *
 * <p>
 * {@link ProcessService} is injected here and nowhere else, which is why the three viewer
 * methods live in this class as well. They are the one part of the SPI which does not
 * change a workflow at all: they read what the BPMS knows, they need no transaction, and
 * they leave the process exactly where it was.
 * </p>
 *
 * <p>
 * The incoming half, what the process tells the application, is
 * {@link WorkflowTaskHandler}.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Viewing-workflows">Viewing
 *      workflows</a>
 */
@Component
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> processService;

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
   * The partner approved, so the task the workflow waits at is completed.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the task to complete.
   */
  public void partnerApproved(
      final Aggregate loanApproval,
      final String taskId) {

    processService.completeTask(loanApproval, taskId);

  }

  /**
   * The process definitions this workflow uses: the one it runs on, plus the ones its
   * call activities would call next. A process without call activities returns exactly
   * one.
   *
   * <p>
   * The {@code historyContext} is {@code null} for the workflow itself. A viewer digging
   * into a call activity passes the {@code secondaryWorkflowHistoryContext} of that
   * element here, which is how the tree of processes is walked.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param historyContext The context of a call activity, or {@code null}.
   * @return The process definitions.
   */
  public List<ProcessDefinition> processDefinitions(
      final Aggregate loanApproval,
      final String historyContext) {

    return processService.getProcessDefinitions(loanApproval, historyContext);

  }

  /**
   * The BPMN XML of one process definition, which is what a viewer draws.
   *
   * <p>
   * The definition id is an OPAQUE string: VanillaBP namespaces the BPMS' own id with the
   * adapter which can resolve it, because a workflow may run on any configured BPMS and
   * this method has no aggregate to derive one from. Pass it back unchanged.
   * </p>
   *
   * @param processDefinitionId The id of a definition, as reported.
   * @return The BPMN XML.
   */
  public InputStream bpmnXml(
      final String processDefinitionId) {

    return processService.getBpmnXml(processDefinitionId);

  }

  /**
   * What this workflow has done so far: when it started, when it ended if it did, and its
   * elements in execution order. The element ids are what a viewer colours.
   *
   * @param loanApproval The workflow's aggregate.
   * @param historyContext The context of a call activity, or {@code null}.
   * @return The history.
   */
  public WorkflowHistory history(
      final Aggregate loanApproval,
      final String historyContext) {

    return processService.getWorkflowHistory(loanApproval, historyContext);

  }

}
