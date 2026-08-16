package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskParam;
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
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared by the application would roll back instead, which is the difference between a
 * rejected loan and a loan that looks untouched. VanillaBP does not let that happen
 * unnoticed: such an annotation on this class or on a {@code @WorkflowTask} method fails the
 * boot naming the method, and one on a bean further down the call chain fails the task while
 * it runs.
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
   * Called by VanillaBP when the BPMN service task of the same name is reached. It
   * translates and hands over, which is all a task handler ever does: the aggregate comes
   * from VanillaBP, the provider from an input mapping of the BPMN task, and the work
   * happens in {@link Service}.
   *
   * <p>
   * Whatever the business code does with it, this method neither catches nor wraps it. An
   * exception leaving here is what tells the BPMS to retry, and a {@code TaskException}
   * leaving here is what tells the process to take the error path.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param provider     The rating provider, mapped in the BPMN as {@code ratingProvider}.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval,
      @TaskParam("ratingProvider") final String provider) {

    service.assessCreditRating(loanApproval, provider);

  }

}
