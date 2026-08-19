package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That the
 * caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * A {@code @WorkflowTask} method therefore contains no business logic. It turns what the
 * BPMS delivers (the aggregate, {@code @TaskId}, {@code @TaskEvent}, the multi-instance
 * element and its index) into a call to business code. With a single service task that
 * leaves one line, which is honest: there is nothing to translate. In a multi-instance task
 * or a user task the same method has real work to do, and that work belongs here rather
 * than in the business code.
 * </p>
 *
 * <p>
 * This class uses {@link Service}, and {@link Service} uses {@link Workflow}, never the
 * other way round. Merging the two directions into one class is what would make the two
 * beans depend on each other, a circular reference which only survives with a workaround.
 * Splitting by direction removes the cycle instead of hiding it.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared by the application would roll back instead and throw away what the handler
 * wrote for the process to react to. VanillaBP does not let that happen unnoticed: such an
 * annotation on this class or on a {@code @WorkflowTask} method fails the boot naming the
 * method, and one on a bean further down the call chain fails the task while it runs.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the business code only has to
   * change it.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

}
