package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import lombok.extern.slf4j.Slf4j;

/**
 * The business code of the workflow module. It is bound to the BPMN process
 * {@code loan_approval} and implements the tasks that process contains - nothing in this
 * class knows which BPMS executes it.
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Slf4j
@org.springframework.stereotype.Service
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
@EnableConfigurationProperties(LoanApprovalProperties.class)
@Transactional
public class Service {

  /**
   * Starting workflows, correlating messages and completing tasks all happen through
   * this bean. It is typed by the workflow aggregate, so one exists per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * Starts a workflow. The aggregate is built first and handed to VanillaBP, which
   * persists it and starts the process in the BPMS - both in the same transaction, so a
   * workflow without its aggregate cannot happen.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    processService.startWorkflow(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the method only has to change
   * it.
   *
   * @param loanApproval The workflow's aggregate.
   * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a
   *      task</a>
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    // A real implementation would call a rating service here.
    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

}
