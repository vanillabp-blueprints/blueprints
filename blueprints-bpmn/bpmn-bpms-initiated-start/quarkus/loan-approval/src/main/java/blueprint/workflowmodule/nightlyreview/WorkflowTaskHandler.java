package blueprint.workflowmodule.nightlyreview;

import blueprint.workflowmodule.nightlyreview.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.BpmsStartTrigger;
import io.vanillabp.spi.service.TaskParam;
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
   * <b>The annotation is required for a process the BPMS can start on its own.</b> A timer,
   * a signal or a condition may fire for this process, and without this method the
   * application refuses to boot, naming the process and the method to write. The check runs
   * while the models are deployed, so a missing method is found then and not at three in the
   * morning.
   * </p>
   *
   * <p>
   * The method BUILDS the aggregate and returns it. Nobody handed one in, because nothing of
   * the application has seen this workflow yet, and an object VanillaBP had instantiated
   * would carry none of the application's values. The ID it picks is the ID of the workflow:
   * the BPMS is told about it and finds the aggregate under it from here on.
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
   * @param trigger What made the BPMS start this workflow.
   * @return The aggregate of the review, which VanillaBP saves.
   */
  @WorkflowStartedByBpms
  public Aggregate reviewDue(
      final BpmsStartTrigger trigger) {

    return service.reviewDue(trigger);

  }

  /**
   * Called by VanillaBP when the service task of the review is reached. From here on this
   * is an ordinary workflow: how it started is not visible any more.
   *
   * @param review     The workflow's aggregate.
   * @param reviewedAt The moment the model wrote into a process variable of its own.
   */
  @WorkflowTask
  public void reviewPendingApprovals(
      final Aggregate review,
      @TaskParam("reviewedAt") final String reviewedAt) {

    service.reviewPendingApprovals(review, reviewedAt);

  }

}
