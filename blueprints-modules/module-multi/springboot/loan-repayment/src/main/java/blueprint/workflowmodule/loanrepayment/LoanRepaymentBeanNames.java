package blueprint.workflowmodule.loanrepayment;

import blueprint.commons.WorkflowModuleBeanNameGenerator;

/**
 * Names this module's beans {@code loan-repayment_<SimpleName>}, the twin of the loan
 * approval's generator.
 */
public class LoanRepaymentBeanNames extends WorkflowModuleBeanNameGenerator {

  public LoanRepaymentBeanNames() {

    super("loan-repayment");

  }

}
