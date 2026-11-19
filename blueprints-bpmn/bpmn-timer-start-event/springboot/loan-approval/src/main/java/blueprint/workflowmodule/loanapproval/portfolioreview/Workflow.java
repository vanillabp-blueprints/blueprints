package blueprint.workflowmodule.loanapproval.portfolioreview;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.portfolioreview.model.Aggregate;
import io.vanillabp.spi.process.ProcessService;

/**
 * What the application tells the review process, which is very little.
 *
 * <p>
 * There is no {@code startWorkflow} here. The timer starts a review without any caller. The one
 * thing the application can do is ask, by broadcasting the signal which the second start event
 * listens for. Even then the engine decides that a workflow begins.
 * </p>
 *
 * <p>
 * The bean is named explicitly, like every class of a second use case in one workflow module.
 * </p>
 */
@Component("portfolioReviewWorkflow")
@Transactional
public class Workflow {

  /**
   * Used for the broadcast only. The service is typed by this use case's aggregate. For a
   * signal, that decides the workflow module it is broadcast in, not who receives it.
   */
  @Autowired
  private ProcessService<Aggregate> bpms;

  /**
   * The name of the BPMN signal the second start event listens for. The same string is the
   * name of the <code>bpmn:signal</code> in the model.
   */
  public static final String REVIEW_REQUESTED = "PortfolioReviewRequested";

  /**
   * Somebody wants a review without waiting for the timer.
   *
   * <p>
   * A broadcast reaches every element of the workflow module which waits for that name, start
   * events included. The caller passes no aggregate and gets no workflow back.
   * </p>
   */
  public void reviewRequested() {

    bpms.sendSignal(REVIEW_REQUESTED);

  }

}
