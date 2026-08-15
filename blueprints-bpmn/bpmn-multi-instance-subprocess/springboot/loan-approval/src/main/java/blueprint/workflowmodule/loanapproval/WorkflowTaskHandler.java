package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.MultiInstanceElement;
import io.vanillabp.spi.service.MultiInstanceIndex;
import io.vanillabp.spi.service.MultiInstanceTotal;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;
import lombok.extern.slf4j.Slf4j;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That the
 * caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * Two iterations are active inside the subprocess, so a parameter has to say WHICH of them
 * it asks about - that is what the BPMN id in every multi-instance annotation is for. This
 * class shows both ways of asking:
 * </p>
 * <ul>
 * <li>{@link #summariseRegion} names one element and takes the three values of that level.
 * It is the plain form, and it is what a method needs when it belongs to one level;</li>
 * <li>{@link #requestPartnerOffer} names a resolver bean, which is handed every active
 * iteration at once and builds one object out of them. That is the answer for a method
 * sitting inside several loops.</li>
 * </ul>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it owns,
 * and it commits that transaction for a {@code TaskException} on purpose. A transaction
 * declared by the application would roll back instead and throw away what the handler
 * wrote for the process to react to. VanillaBP does not let that happen unnoticed: such an
 * annotation on this class or on a {@code @WorkflowTask} method fails the boot naming the
 * method, and one on a bean further down the call chain fails the task while it runs.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#multi-instance">Multi-instance</a>
 */
@Slf4j
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Autowired
  private Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached, before the
   * subprocess and therefore exactly once.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * Called once per partner and region, so regions times partners in total.
   *
   * <p>
   * The resolver builds {@link Iteration} out of both active iterations. Asking for the
   * element of the subprocess and the element of this task separately would work as well;
   * this way the method is handed one object which says what the pair means.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param iteration    Where this call sits: which region, which partner.
   */
  @WorkflowTask
  public void requestPartnerOffer(
      final Aggregate loanApproval,
      @MultiInstanceElement(resolverBean = IterationResolver.class) final Iteration iteration) {

    log.info(
        "Asking partner {} of {} in region {} of {} for loan approval '{}'",
        iteration.partnerIndex() + 1,
        iteration.partners(),
        iteration.regionIndex() + 1,
        iteration.regions(),
        loanApproval.getLoanRequestId());

    service.requestPartnerOffer(loanApproval, iteration.regionId(), iteration.partnerId());

  }

  /**
   * Called once per region, at the end of that region's assessment. The task is not
   * multi-instance itself and still runs inside an iteration, which it asks the subprocess
   * about by naming it.
   *
   * @param loanApproval The workflow's aggregate.
   * @param regionId     The region this iteration of the subprocess runs for.
   * @param index        Which region this is, counted from zero.
   * @param total        How many regions there are.
   */
  @WorkflowTask
  public void summariseRegion(
      final Aggregate loanApproval,
      @MultiInstanceElement(IterationResolver.ASSESS_REGION) final String regionId,
      @MultiInstanceIndex(IterationResolver.ASSESS_REGION) final int index,
      @MultiInstanceTotal(IterationResolver.ASSESS_REGION) final int total) {

    log.info(
        "Finishing region {} of {} for loan approval '{}'",
        index + 1,
        total,
        loanApproval.getLoanRequestId());

    service.summariseRegion(loanApproval, regionId, index);

  }

  /**
   * Called once, after the last iteration of the subprocess has finished.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void chooseBestOffer(
      final Aggregate loanApproval) {

    service.chooseBestOffer(loanApproval);

  }

}
