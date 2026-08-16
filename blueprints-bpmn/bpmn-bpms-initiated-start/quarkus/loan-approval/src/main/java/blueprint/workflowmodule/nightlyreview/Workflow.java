package blueprint.workflowmodule.nightlyreview;

import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * What the application tells the review process - which is remarkably little.
 *
 * <p>
 * <b>There is no {@code startWorkflow} here, and that is the blueprint.</b> A workflow the
 * BPMS starts on its own has no caller: the timer fires, or a broadcast signal arrives, and
 * the application only hears about it afterwards. The one thing the application can do is
 * ask, by broadcasting the signal the second start event listens for - and even then it is
 * the engine that decides a workflow begins.
 * </p>
 *
 * <p>
 * The bean is named explicitly, like every class of a second use case in one workflow
 * module.
 * </p>
 */
@ApplicationScoped
@Transactional
public class Workflow {

  /**
   * Used for the broadcast only. The service is typed by this use case's aggregate, which
   * for a signal decides the workflow module it is broadcast in, not who receives it.
   */
  @Inject
  ProcessService<Aggregate> processService;

  /**
   * The name of the BPMN signal the second start event listens for. The same string is the
   * name of the <code>bpmn:signal</code> in the model.
   */
  public static final String REVIEW_REQUESTED = "ReviewRequested";

  /**
   * Somebody wants a review without waiting for the timer.
   *
   * <p>
   * A broadcast reaches every element of the workflow module waiting for that name,
   * including start events - and starting a process is precisely what a signal start event
   * does with it. The caller passes no aggregate and gets no workflow back: what the
   * broadcast starts is the engine's business.
   * </p>
   */
  public void reviewRequested() {

    processService.sendSignal(REVIEW_REQUESTED);

  }

}
