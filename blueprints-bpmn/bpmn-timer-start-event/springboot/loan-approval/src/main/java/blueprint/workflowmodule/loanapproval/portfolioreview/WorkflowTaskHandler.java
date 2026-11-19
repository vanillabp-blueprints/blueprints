package blueprint.workflowmodule.loanapproval.portfolioreview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.portfolioreview.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.BpmsStartTrigger;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowStartedByBpms;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the review process tells the application. The process has two start events which the
 * BPMS fires on its own, and each of them has a method of its own here.
 *
 * <p>
 * The bean is named explicitly. Every use case of the reference structure has a class called
 * {@code WorkflowTaskHandler}, so the second one in a module says which bean it is.
 * </p>
 */
@Component("portfolioReviewTaskHandler")
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "portfolio_review"))
public class WorkflowTaskHandler {

  /** The BPMN id of the timer start event. */
  public static final String SCHEDULED_REVIEW = "StartEvent_ScheduledReview";

  /** The BPMN id of the signal start event. */
  public static final String REVIEW_REQUESTED = "StartEvent_ReviewRequested";

  @Autowired
  private Service portfolioReview;

  /**
   * Called by VanillaBP when the timer start event fired, and only then.
   *
   * <p>
   * {@code id} names the start event this method serves. The other start event of the process
   * never reaches it. So the method knows what happened without asking. It takes no
   * {@code BpmsStartTrigger}, because the trigger could only tell it what the annotation
   * already says.
   * </p>
   *
   * <p>
   * The method BUILDS the aggregate and returns it, and VanillaBP saves it before anything
   * else of the process runs. The ID it picks is the ID of the workflow.
   * </p>
   *
   * @return The aggregate of a scheduled review.
   */
  @WorkflowStartedByBpms(id = SCHEDULED_REVIEW)
  public Aggregate reviewIsDue() {

    return portfolioReview.scheduled();

  }

  /**
   * Called by VanillaBP when the signal start event received a broadcast, and only then.
   *
   * <p>
   * This method takes the trigger, to keep the name of the signal on the aggregate. A method
   * which serves one start event needs the trigger only for such a detail.
   * </p>
   *
   * @param trigger What made the BPMS start this workflow. Its kind is always {@code SIGNAL}
   *                here.
   * @return The aggregate of a review somebody asked for.
   */
  @WorkflowStartedByBpms(id = REVIEW_REQUESTED)
  public Aggregate reviewWasRequested(
      final BpmsStartTrigger trigger) {

    return portfolioReview.requested(trigger.signalName());

  }

  /**
   * Called by VanillaBP when the service task of the review is reached. Both start events lead
   * here. From here on this is an ordinary workflow, and how it started is not visible any
   * more.
   *
   * @param review The workflow's aggregate.
   */
  @WorkflowTask
  public void reviewPortfolio(
      final Aggregate review) {

    portfolioReview.reviewPortfolio(review);

  }

}
