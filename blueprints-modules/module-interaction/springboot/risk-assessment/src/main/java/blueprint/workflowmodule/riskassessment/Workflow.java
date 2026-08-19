package blueprint.workflowmodule.riskassessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.riskassessment.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What this module tells its own process: the outgoing half of its BPMN wiring.
 *
 * <p>
 * Note what is not here: a method the other module could call. The border between two
 * workflow modules runs through {@code Service}, which implements the interface the API JAR
 * publishes; {@code ProcessService} stays behind it, in this class, as in every blueprint.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-process">Wire up a
 *      process</a>
 */
@Component
@Transactional
public class Workflow {

  @Autowired
  private ProcessService<Aggregate> processService;

  /**
   * An assessment was asked for. VanillaBP persists the aggregate and starts the process in
   * the same transaction.
   *
   * @param assessment The workflow's aggregate.
   */
  public void assessmentRequested(
      final Aggregate assessment) {

    processService.startWorkflow(assessment);

  }

}
