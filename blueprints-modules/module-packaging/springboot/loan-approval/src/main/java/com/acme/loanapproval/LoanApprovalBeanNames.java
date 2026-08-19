package com.acme.loanapproval;

import com.acme.banking.WorkflowModuleBeanNameGenerator;

/**
 * Names this module's beans {@code loan-approval_<SimpleName>}. One line per module, and the
 * only thing the generator has to be told is which module it works for.
 */
public class LoanApprovalBeanNames extends WorkflowModuleBeanNameGenerator {

  public LoanApprovalBeanNames() {

    super("loan-approval");

  }

}
