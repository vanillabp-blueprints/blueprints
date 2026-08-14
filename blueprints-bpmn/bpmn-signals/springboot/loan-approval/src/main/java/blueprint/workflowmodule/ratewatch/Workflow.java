package blueprint.workflowmodule.ratewatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.ratewatch.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

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
@Component("rateWatchWorkflow")
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> processService;

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
