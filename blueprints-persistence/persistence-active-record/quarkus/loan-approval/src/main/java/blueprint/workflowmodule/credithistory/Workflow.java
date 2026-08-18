package blueprint.workflowmodule.credithistory;

import blueprint.workflowmodule.credithistory.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * What the application tells the process of this use case: the outgoing half of the BPMN
 * wiring.
 *
 * <p>
 * The class is the loan approval's twin, down to the injected {@link ProcessService}. That
 * bean is typed by the workflow aggregate, so there is one per workflow, and the one injected
 * here serves the aggregate stored in MongoDB while the other one serves the JPA entity.
 * Nothing in this class says which is which.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  @Inject
  ProcessService<Aggregate> processService;

  /**
   * A credit history was requested. VanillaBP persists the aggregate and starts the process in
   * the same transaction, so a workflow without its aggregate cannot happen.
   *
   * @param creditHistory The workflow's aggregate.
   */
  public void creditHistoryRequested(
      final Aggregate creditHistory) {

    processService.startWorkflow(creditHistory);

  }

}
