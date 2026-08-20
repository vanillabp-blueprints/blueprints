package blueprint.workflowmodule.loanrepayment;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanrepayment.model.Repayment;
import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the repayment process.
 *
 * <p>
 * The code is the same as the one of the other workflow of this module, and so is the way it
 * starts a workflow. What differs is one line of configuration: this workflow has a priority
 * list naming the old BPMS, so it starts there while loan approvals already start in the new
 * one.
 * </p>
 */
@Component
@Transactional
public class RepaymentWorkflow {

  @Autowired
  private ProcessService<Repayment> processService;

  /**
   * A repayment is due.
   *
   * @param repayment The workflow's aggregate.
   */
  public void repaymentDue(
      final Repayment repayment) {

    processService.startWorkflow(repayment);

  }

  /**
   * @param repayment The workflow's aggregate.
   * @return The definitions this workflow runs on, whose ids name the adapter serving them.
   */
  public List<ProcessDefinition> definitionsOf(
      final Repayment repayment) {

    return processService.getProcessDefinitions(repayment, null);

  }

}
