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
 * <strong>Two processes, one aggregate.</strong> {@code loan_approval} is the process the
 * application starts, {@code risk_assessment} is the one its call activity calls, and
 * {@code secondaryBpmnProcesses} is what tells VanillaBP that the tasks of both work on the
 * same workflow aggregate. That is what decomposition means: the model was split to stay
 * readable, the business case was not.
 * </p>
 *
 * <p>
 * Declare every called process here, as a secondary process, rather than as the
 * {@code bpmnProcess} of a class of its own. VanillaBP builds one {@code ProcessService} per
 * workflow aggregate class, and exactly one process is the one {@code startWorkflow} starts.
 * Two classes on the same aggregate which declared different processes fail the boot, naming
 * both. The tasks of the called process may still live in a class of their own, if that class
 * declares the same {@code bpmnProcess} and the same secondary process. The class
 * {@link blueprint.workflowmodule.loanapproval.riskassessment.WorkflowTaskHandler} does that.
 * A called process with an aggregate of its own is a different situation altogether; the
 * README says what VanillaBP does with that one.
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
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP loads
 * the aggregate, runs the method and saves the aggregate in one transaction it owns, and it
 * commits that transaction for a {@code TaskException} on purpose. A transaction declared by
 * the application would roll back instead and throw away what the handler wrote for the
 * process to react to. VanillaBP does not let that happen unnoticed: such an annotation on
 * this class or on a {@code @WorkflowTask} method fails the boot naming the method, and a
 * plain one on a bean further down the call chain fails the task as soon as a
 * {@code TaskException} passes it.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java/wiki/Call-activities">Call
 *      activities</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"),
    secondaryBpmnProcesses = @BpmnProcess(bpmnProcessId = "risk_assessment"))
public class WorkflowTaskHandler {

  @Inject
  Service loanApproval;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the business code only has to
   * change it.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanRequest) {

    loanApproval.assessCreditRating(loanRequest);

  }

  /**
   * Called after the call activity returned. The aggregate it is handed carries what the
   * called process wrote, because both processes work on the same one.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void decideOnLoan(
      final Aggregate loanRequest) {

    loanApproval.decideOnLoan(loanRequest);

  }

}
