package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the processes tell the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That the
 * caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * <strong>Two processes, one handler, one aggregate.</strong> {@code loan_approval} is the
 * process the application starts, {@code risk_assessment} is the one its call activity
 * calls, and {@code secondaryBpmnProcesses} is what tells VanillaBP that the tasks of both
 * belong to this class and work on the same workflow aggregate. That is what decomposition
 * means: the model was split to stay readable, the business case was not.
 * </p>
 *
 * <p>
 * Declare every called process here rather than in a handler class of its own. VanillaBP
 * builds one {@code ProcessService} per workflow aggregate class, and the process it
 * starts is the one of the first workflow service class it happens to find - with a second
 * class on the same aggregate, {@code startWorkflow} may start the called process instead
 * of the calling one, and nothing says so. A called process with an aggregate of its own is
 * a different situation altogether; the README says what VanillaBP does with that one.
 * </p>
 *
 * <p>
 * <strong>The call activity has no method here, and needs none.</strong> A call activity is
 * executed by the BPMS itself: it starts the called process and waits for it. There is
 * nothing for the application to do, which is why a model can be split without a line of
 * code changing.
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
 * @see <a href="https://github.com/vanillabp/spi-for-java#call-activities">Call
 *      activities</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"),
    secondaryBpmnProcesses = @BpmnProcess(bpmnProcessId = "risk_assessment"))
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

  /**
   * A task of the called process. Nothing marks it as one: it is handed the aggregate of
   * the loan approval like every other task, because the called process is a section of
   * the same business case.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void checkCollateral(
      final Aggregate loanApproval) {

    service.checkCollateral(loanApproval);

  }

  /**
   * The second task of the called process.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void checkDebtRatio(
      final Aggregate loanApproval) {

    service.checkDebtRatio(loanApproval);

  }

  /**
   * Called after the call activity returned. The aggregate it is handed carries what the
   * called process wrote, because both processes work on the same one.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void decideOnLoan(
      final Aggregate loanApproval) {

    service.decideOnLoan(loanApproval);

  }

}
