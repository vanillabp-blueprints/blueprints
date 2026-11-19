package blueprint.workflowmodule.loanapproval.creditcheck;

import blueprint.workflowmodule.loanapproval.creditcheck.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * What the application tells the credit check process.
 *
 * <p>
 * Its {@link ProcessService} is typed by the aggregate of the credit check. That is what
 * makes the credit check a process of its own: it is started by {@code startWorkflow}, not by
 * a call activity of another process.
 * </p>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  @Inject
  ProcessService<Aggregate> bpms;

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
