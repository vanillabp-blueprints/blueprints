package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * Nothing in this class knows about diagrams or histories. That is the point of the
 * blueprint: viewing a workflow is a question the application asks the BPMS, never
 * something a handler has to collect along the way.
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
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Autowired
  private Service service;

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
   * The task the workflow waits at. The {@code @TaskId} parameter is what keeps it open:
   * returning from this method does not complete it, so the workflow stands here until the
   * application says otherwise - and a standing workflow is what makes a history worth
   * looking at.
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of this task.
   */
  @WorkflowTask
  public void awaitPartnerApproval(
      final Aggregate loanApproval,
      @TaskId final String taskId) {

    service.awaitPartnerApproval(loanApproval, taskId);

  }

  /**
   * Called after the partner approved.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void decideOnLoan(
      final Aggregate loanApproval) {

    service.decideOnLoan(loanApproval);

  }

}
