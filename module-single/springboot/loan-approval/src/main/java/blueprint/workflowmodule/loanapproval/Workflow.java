package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import lombok.extern.slf4j.Slf4j;

/**
 * The port between the business code and the BPMN process: this is the only class knowing
 * that there is a process at all.
 *
 * <p>
 * Everything BPMN-facing lives here - {@link ProcessService} is injected nowhere else, and
 * every {@code @WorkflowTask} method is a method of this class. {@link Service} calls in,
 * naming what happened in business terms ({@code loanRequested}), and this class translates
 * it into whatever the process needs: starting a workflow, correlating a message,
 * completing a task. Keeping that translation in one place is what allows the business code
 * to stay readable when the process grows.
 * </p>
 *
 * <p>
 * In this blueprint the translation is a single line, which is exactly why it is worth
 * showing here: the shape stays the same when it stops being one.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Slf4j
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
@EnableConfigurationProperties(LoanApprovalProperties.class)
@Transactional
public class Workflow {

  /**
   * Starting workflows, correlating messages and completing tasks all happen through this
   * bean. It is typed by the workflow aggregate, so there is one per workflow.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  @Autowired
  private LoanApprovalProperties properties;

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
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the method only has to change
   * it.
   *
   * <p>
   * A real application would delegate the work to a domain service here; what must not
   * move out of this class is the BPMN wiring.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a
   *      task</a>
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

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
