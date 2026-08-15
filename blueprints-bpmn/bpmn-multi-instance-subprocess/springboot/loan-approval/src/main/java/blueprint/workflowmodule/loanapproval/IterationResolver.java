package blueprint.workflowmodule.loanapproval;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.MultiInstanceElementResolver;

/**
 * Turns the two iterations a nested task runs in into one object.
 *
 * <p>
 * VanillaBP hands a resolver every active multi-instance context, keyed by the BPMN id of
 * the element and ordered from the outermost iteration to the innermost. That is what makes
 * it the answer for nesting: a method can be handed something built from ALL levels instead
 * of a parameter per level.
 * </p>
 *
 * <p>
 * A resolver is a bean, and it is named in the annotation:
 * {@code @MultiInstanceElement(resolverBean = IterationResolver.class)}. The plain form,
 * {@code @MultiInstanceElement("<element id>")}, stays the right choice when one value from
 * one level is all a method needs - {@code summariseRegion} of this blueprint does it that
 * way.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#multi-instance">Multi-instance</a>
 */
@Component
public class IterationResolver implements MultiInstanceElementResolver<Aggregate, Iteration> {

  /** The BPMN id of the multi-instance subprocess. */
  static final String ASSESS_REGION = "SubProcess_AssessRegion";

  /** The BPMN id of the multi-instance task inside it. */
  static final String REQUEST_PARTNER_OFFER = "ServiceTask_RequestPartnerOffer";

  @Override
  public Collection<String> getNames() {

    return List.of(ASSESS_REGION, REQUEST_PARTNER_OFFER);

  }

  @Override
  public Iteration resolve(
      final Aggregate loanApproval,
      final Map<String, MultiInstance<Object>> multiInstances) {

    final var region = multiInstances.get(ASSESS_REGION);
    final var partner = multiInstances.get(REQUEST_PARTNER_OFFER);

    return new Iteration(
        (String) region.getElement(), region.getIndex(), region.getTotal(), (String) partner.getElement(), partner
            .getIndex(), partner.getTotal());

  }

}
