package blueprint.workflowmodule.loanapproval.creditcheck;

import blueprint.workflowmodule.loanapproval.creditcheck.api.CreditCheckApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The API of the credit check. Nobody starts a credit check here, because only other
 * processes do that. This API takes the answer of the agency and shows a credit check.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link CreditCheckApi}, the interface the
 * build generates from {@code src/main/resources/loan-approval/openapi/credit-check.yaml}. This
 * class only implements it.
 * </p>
 */
@ApplicationScoped
public class ApiController implements CreditCheckApi {

  @Inject
  Service creditCheck;

  /**
   * The credit agency answers. This is the callback the agency would call.
   *
   * @param creditCheckId The id of the credit check, as logged.
   * @param score         The score the agency gives.
   * @return What was done, for the browser to show.
   */
  @Override
  public String agencyAnswered(
      final String creditCheckId,
      final Integer score) {

    creditCheck.agencyAnswered(creditCheckId, score);

    return "The agency answered credit check '"
        + creditCheckId
        + "'";

  }

  /**
   * Shows what the credit check did.
   *
   * @param creditCheckId The id of the credit check, as logged.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String creditCheckId) {

    return creditCheck
        .get(creditCheckId)
        .map(Object::toString)
        .orElse("unknown credit check '"
            + creditCheckId
            + "'");

  }

}
