package blueprint.workflowmodule.nightlyreview;

import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.BpmsStartTrigger;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowStartedByBpms;
import io.vanillabp.spi.service.WorkflowTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * What the review process tells the application, including the one message no other
 * blueprint gets: "I started, and nobody asked me to".
 *
 * <p>
 * The bean is named explicitly. Every use case of the reference structure has a class
 * called {@code WorkflowTaskHandler}, so the second one in a module says which bean it is.
 * </p>
 */
@ApplicationScoped
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "nightly_review"))
public class WorkflowTaskHandler {

  @Inject
  Service service;

  /**
   * Called by VanillaBP when the engine started this workflow by itself.
   *
   * <p>
   * <b>The annotation is optional.</b> Leave it out and VanillaBP still builds the
   * aggregate, assigns its ID and copies process variables of the model into attributes of
   * the same name - this blueprint would run with an empty class here. It exists to show
   * the hook: the aggregate VanillaBP built is handed in, together with a
   * {@link BpmsStartTrigger} saying what fired, and whatever is written here is saved with
   * it in the same transaction.
   * </p>
   *
   * <p>
   * Without {@code id} the method serves every BPMS-initiated start event of the process,
   * which is what a process with one such event needs. This one has two, and both are meant
   * to end up in the same aggregate, so no id is named. Naming one
   * ({@code @WorkflowStartedByBpms(id = "StartEvent_ScheduledReview")}) is how the two would
   * be told apart.
   * </p>
   *
   * <p>
   * Throwing here means the workflow does not start: the aggregate is rolled back and the
   * BPMS applies its retry semantics.
   * </p>
   *
   * @param review  The aggregate VanillaBP built and is about to save.
   * @param trigger What made the BPMS start this workflow.
   */
  @WorkflowStartedByBpms
  public void reviewDue(
      final Aggregate review,
      final BpmsStartTrigger trigger) {

    service.reviewDue(review, trigger);

  }

  /**
   * Called by VanillaBP when the service task of the review is reached. From here on this
   * is an ordinary workflow: how it started is not visible any more.
   *
   * @param review The workflow's aggregate.
   */
  @WorkflowTask
  public void reviewPendingApprovals(
      final Aggregate review) {

    service.reviewPendingApprovals(review);

  }

}
