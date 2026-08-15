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
 * The multi-instance task is where such a method has real work to do. It is handed the
 * element of the iteration, its index and the total, and it turns that into a call naming
 * one partner. Everything the business code learns about the iteration comes through this
 * class - {@link Service} takes a partner and an iteration number, not a BPMN concept.
 * </p>
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

  /**
   * The ID of the multi-instance element in the BPMN. It is how a parameter says WHICH
   * iteration it wants to know about, which matters as soon as a multi-instance task sits
   * inside a multi-instance subprocess - then more than one iteration is running around
   * this method.
   */
  private static final String REQUEST_PARTNER_OFFER = "ServiceTask_RequestPartnerOffer";

  @Autowired
  private Service service;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the business code only has to
   * change it.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanApproval) {

    service.assessCreditRating(loanApproval);

  }

  /**
   * Called once per element of the collection the multi-instance task iterates over, all
   * of them at the same time.
   *
   * <p>
   * The three annotated parameters are what the BPMS knows about the iteration: the element
   * it was handed, which iteration it is (counted from zero) and how many there are. Each
   * of them names the multi-instance element in the model, because a method may sit inside
   * several nested iterations at once.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param partnerId    The element of this iteration.
   * @param index        Which iteration this is, counted from zero.
   * @param total        How many iterations there are.
   */
  @WorkflowTask
  public void requestPartnerOffer(
      final Aggregate loanApproval,
      @MultiInstanceElement(REQUEST_PARTNER_OFFER) final String partnerId,
      @MultiInstanceIndex(REQUEST_PARTNER_OFFER) final int index,
      @MultiInstanceTotal(REQUEST_PARTNER_OFFER) final int total) {

    log.info(
        "Asking partner {} of {} for loan approval '{}'",
        index + 1,
        total,
        loanApproval.getLoanRequestId());

    service.requestPartnerOffer(loanApproval, partnerId, index);

  }

  /**
   * Called once, after the last iteration has finished.
   *
   * @param loanApproval The workflow's aggregate.
   */
  @WorkflowTask
  public void chooseBestOffer(
      final Aggregate loanApproval) {

    service.chooseBestOffer(loanApproval);

  }

}
