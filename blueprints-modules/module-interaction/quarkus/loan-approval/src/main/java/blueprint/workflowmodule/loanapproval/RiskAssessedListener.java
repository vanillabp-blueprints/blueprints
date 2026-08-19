package blueprint.workflowmodule.loanapproval;

import blueprint.api.RiskAssessed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Where the answer of the other workflow module enters this one: an event arrives, and this
 * class turns it into something the use case understands.
 *
 * <p>
 * It is a driving adapter, the same kind of thing as {@code ApiController}: something
 * outside triggers, and the trigger becomes a call to {@link Service}. That the trigger
 * comes from another module of the same application rather than from a browser changes
 * nothing about the direction, and it is the reason this class exists instead of the other
 * module calling into this one.
 * </p>
 *
 * <p>
 * The BPMN of this process knows none of it. It waits for a message called
 * {@code RiskAssessed}, and who caused that message is not modelled anywhere.
 * </p>
 */
@ApplicationScoped
public class RiskAssessedListener {

  @Inject
  Service service;

  /**
   * The risk assessment published its verdict.
   *
   * @param event What the other module worked out.
   */
  public void onRiskAssessed(
      @Observes final RiskAssessed event) {

    service.riskAssessed(event.caseId(), event.score());

  }

}
