package blueprint.workflowmodule.loanapproval.creditcheck;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.creditcheck.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the credit check process.
 *
 * <p>
 * Its {@link ProcessService} is typed by the aggregate of the credit check. That is what
 * makes the credit check a process of its own: it is started by {@code startWorkflow}, not by
 * a call activity of another process.
 * </p>
 *
 * <p>
 * The bean is named explicitly. Every use case of the reference structure has a class called
 * {@code Workflow}, so each further one in a module says which bean it is.
 * </p>
 */
@Component("creditCheckWorkflow")
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * The name of the BPMN message the process waits for. The same string is the name of the
   * <code>bpmn:message</code> in the model.
   */
  public static final String AGENCY_ANSWERED = "AgencyAnswered";

  /**
   * Somebody asked for a credit check.
   *
   * @param creditCheck The workflow's aggregate.
   */
  public void creditCheckRequested(
      final Aggregate creditCheck) {

    bpms.startWorkflow(creditCheck);

  }

  /**
   * The agency answered, which is the message the workflow waits for.
   *
   * @param creditCheck The workflow's aggregate.
   */
  public void agencyAnswered(
      final Aggregate creditCheck) {

    bpms.correlateMessage(creditCheck, AGENCY_ANSWERED);

  }

}
