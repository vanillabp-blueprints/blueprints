package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowEnd;
import io.vanillabp.spi.service.WorkflowEnded;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * The end notification belongs here for the same reason the tasks do: it is the BPMS
 * calling in. {@code @WorkflowEnded} marks the method, VanillaBP loads the aggregate,
 * calls it and saves the aggregate - the same contract a {@code @WorkflowTask} method has.
 * </p>
 *
 * <p>
 * <strong>The notification is at-least-once</strong>, so what the method does has to
 * survive arriving twice. Writing a closing time and a status does; sending a letter would
 * not, and belongs in front of the end event as a task.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared by the application would roll back instead and throw away what the handler
 * wrote for the process to react to.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-tasks">Workflow
 *      tasks</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * Called on the branch which informs the customer - a task in front of an end event,
   * because informing somebody is work.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void informCustomer(
      final Aggregate loanApproval) {

    service.informCustomer(loanApproval);

  }

  /**
   * Called once the workflow ended, whichever end event it reached and whether anything was
   * modelled in front of that end event or not. Without this method the application would
   * have to put a task before EVERY end event just to be told.
   *
   * <p>
   * The method may take the aggregate and a {@link WorkflowEnd} in any order, and either of
   * them alone. Nothing else is offered on purpose: what the workflow did is on the
   * aggregate, and what the BPMS knows about the end is in this record.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param end          How and when it ended.
   */
  @WorkflowEnded
  public void loanApprovalEnded(
      final Aggregate loanApproval,
      final WorkflowEnd end) {

    service.loanApprovalClosed(loanApproval, end);

  }

}
