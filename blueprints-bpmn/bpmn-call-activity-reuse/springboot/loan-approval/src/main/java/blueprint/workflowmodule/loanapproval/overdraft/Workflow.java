package blueprint.workflowmodule.loanapproval.overdraft;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.overdraft.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the overdraft request process.
 *
 * <p>
 * The bean is named explicitly. Every use case of the reference structure has a class called
 * {@code Workflow}, so each further one in a module says which bean it is.
 * </p>
 */
@Component("overdraftWorkflow")
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * The name of the BPMN message the process waits for. It is the same name the loan
   * approval waits for. Each process has a message of its own in its own model, and the
   * correlation finds the workflow by the aggregate's id, so the two never meet.
   */
  public static final String CREDIT_CHECKED = "CreditChecked";

  /**
   * An overdraft was requested.
   *
   * @param overdraft The workflow's aggregate.
   */
  public void overdraftRequested(
      final Aggregate overdraft) {

    bpms.startWorkflow(overdraft);

  }

  /**
   * The credit check reported its result, which is the message the workflow waits for.
   *
   * @param overdraft The workflow's aggregate.
   */
  public void creditChecked(
      final Aggregate overdraft) {

    bpms.correlateMessage(overdraft, CREDIT_CHECKED);

  }

}
