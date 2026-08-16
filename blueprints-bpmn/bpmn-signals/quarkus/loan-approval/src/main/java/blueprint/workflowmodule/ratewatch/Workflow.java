package blueprint.workflowmodule.ratewatch;

import blueprint.workflowmodule.ratewatch.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * What the application tells the rate watch process.
 *
 * <p>
 * There is no {@code sendSignal} here, and that is what this use case is about: nobody has
 * to send the signal twice. The loan approval broadcasts it, and this process catches it
 * because both live in the same workflow module.
 * </p>
 *
 * <p>
 * The bean is named explicitly. Every use case of the reference structure has a class
 * called {@code Workflow}, so the second one in a module says which bean it is.
 * </p>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  @Inject
  ProcessService<Aggregate> processService;

  /**
   * Somebody wants to be told about the next published rate.
   *
   * @param rateWatch The workflow's aggregate.
   */
  public void watchRequested(
      final Aggregate rateWatch) {

    processService.startWorkflow(rateWatch);

  }

}
