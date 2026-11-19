package blueprint.workflowmodule.loanapproval.creditcheck;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.creditcheck.api.CreditCheckApi;

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
@RestController("creditCheckApiController")
public class ApiController implements CreditCheckApi {

  @Autowired
  private Service creditCheck;

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
