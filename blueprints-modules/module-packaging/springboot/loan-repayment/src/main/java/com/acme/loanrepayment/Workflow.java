package com.acme.loanrepayment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.acme.loanrepayment.model.Aggregate;

import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the process of this use case: the outgoing half of the BPMN
 * wiring. Its twin in the other module is
 * {@link com.acme.loanapproval.Workflow} - same shape, different process,
 * no shared code.
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  /**
   * Typed by the workflow aggregate, so each workflow module gets the bean serving its own
   * process even though both live in one application context.
   */
  @Autowired
  private ProcessService<Aggregate> processService;

  /**
   * A repayment was agreed. VanillaBP persists the aggregate and starts the process in the
   * same transaction.
   *
   * @param repayment The workflow's aggregate.
   */
  public void repaymentAgreed(
      final Aggregate repayment) {

    processService.startWorkflow(repayment);

  }

}
