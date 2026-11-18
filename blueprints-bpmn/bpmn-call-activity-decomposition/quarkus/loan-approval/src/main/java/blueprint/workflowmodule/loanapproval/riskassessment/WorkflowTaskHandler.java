package blueprint.workflowmodule.loanapproval.riskassessment;

import blueprint.workflowmodule.loanapproval.Service;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The tasks of the called process {@code risk_assessment}, in a class of their own. The
 * called process is a section of the loan approval, so its handlers get a package of their
 * own, as a large workflow is split by its sections.
 *
 * <p>
 * The class declares the same {@code @WorkflowService} as the main handler: {@code loan_approval}
 * as its {@code bpmnProcess} and {@code risk_assessment} as a secondary process. The same
 * {@code bpmnProcess} is what makes a second workflow service class on one aggregate fine: there
 * is still one process {@code startWorkflow} starts. The secondary process has to be named here
 * as well, because VanillaBP wires the tasks of a process to the classes which declare it. A
 * class which declared {@code risk_assessment} as its {@code bpmnProcess} would fail the boot,
 * naming both classes.
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
   * A task of the called process. Nothing marks it as one: it is handed the aggregate of
   * the loan approval like every other task, because the called process is a section of
   * the same business case.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void checkCollateral(
      final Aggregate loanRequest) {

    loanApproval.checkCollateral(loanRequest);

  }

  /**
   * The second task of the called process.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void checkDebtRatio(
      final Aggregate loanRequest) {

    loanApproval.checkDebtRatio(loanRequest);

  }

}
