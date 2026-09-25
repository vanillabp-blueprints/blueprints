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
 * <strong>Two sections, one handler.</strong> The first section is the called process
 * {@code document_check}, the second one is an embedded subprocess of
 * {@code loan_approval}, and the methods below serve both. Nothing marks a method as
 * belonging to a section: a task of a section is a task like any other, and it is handed
 * the same workflow aggregate.
 * </p>
 *
 * <p>
 * {@code secondaryBpmnProcesses} names the called process. Put it here rather than into a
 * workflow service class of its own: VanillaBP builds one {@code ProcessService} per
 * workflow aggregate class, and with a second class on the same aggregate
 * {@code startWorkflow} may start the called process instead of the loan approval.
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
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"),
    secondaryBpmnProcesses = @BpmnProcess(bpmnProcessId = "document_check"))
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
   * The first task of the first section, which runs in the called process. It creates the
   * sub-object of that section, which is what makes the section visible in the data.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void collectDocuments(
      final Aggregate loanApproval) {

    service.collectDocuments(loanApproval);

  }

  /**
   * The second task of the first section.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void verifySignatures(
      final Aggregate loanApproval) {

    service.verifySignatures(loanApproval);

  }

  /**
   * The first task of the second section, which runs in the embedded subprocess. It creates
   * that section's sub-object, and a workflow which skips the section never calls this
   * method.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void checkCollateral(
      final Aggregate loanApproval) {

    service.checkCollateral(loanApproval);

  }

  /**
   * The second task of the second section.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void checkDebtRatio(
      final Aggregate loanApproval) {

    service.checkDebtRatio(loanApproval);

  }

  /**
   * The decision of a loan whose risk was assessed.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void decideWithRiskReport(
      final Aggregate loanApproval) {

    service.decideWithRiskReport(loanApproval);

  }

  /**
   * The decision of a loan which skipped the second section.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void decideOnDocumentsAlone(
      final Aggregate loanApproval) {

    service.decideOnDocumentsAlone(loanApproval);

  }

}
