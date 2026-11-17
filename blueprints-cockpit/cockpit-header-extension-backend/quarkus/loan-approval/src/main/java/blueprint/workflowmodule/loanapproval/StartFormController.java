package blueprint.workflowmodule.loanapproval;

import org.jboss.resteasy.reactive.ResponseStatus;

import blueprint.workflowmodule.loanapproval.api.StartFormApi;
import blueprint.workflowmodule.loanapproval.api.model.LoanRequest;
import blueprint.workflowmodule.loanapproval.api.model.StartedLoanRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * The endpoint the start form calls. It implements
 * {@code src/main/resources/openapi/loan-approval.yaml} of this module, which the frontend reads
 * to generate its client.
 *
 * <p>
 * The form opens from a button in the header of the Business Cockpit. It runs in the browser,
 * inside the cockpit, and calls {@code /wm/loan-approval/api/loan-requests}, a path of the
 * cockpit, which is the server the OpenAPI names. The cockpit forwards the request to the
 * {@code workflow-module-uri} of this module and puts the path of that address in place of
 * {@code /wm/loan-approval}. The address ends with {@code /loan-approval}, so the request arrives
 * here as {@code /loan-approval/api/loan-requests}.
 * </p>
 *
 * <p>
 * The form sends a body, so the endpoint is a POST. GET requests are for a person who types a
 * URL into the browser, see {@link ApiController}.
 * </p>
 *
 * <p>
 * The path and the classes {@link LoanRequest} and {@link StartedLoanRequest} come from
 * {@link StartFormApi}, the interface the build generates from the same OpenAPI file. This class
 * only implements it. The OpenAPI names its paths below the server {@code /wm/loan-approval}, so
 * the interface maps {@code /api/loan-requests}. The {@code @Path} of this class puts
 * {@code /loan-approval} in front, which is where the request arrives. Jakarta REST takes the
 * {@code @Path} of a class instead of the one of its interface, not in addition to it. So the
 * class repeats the path of the interface after {@code /loan-approval}.
 * </p>
 */
@ApplicationScoped
@Path("/loan-approval/api/loan-requests")
public class StartFormController implements StartFormApi {

  @Inject
  Service loanApproval;

  /**
   * Starts a loan approval with what the user typed into the start form ({@code requestLoan}).
   * The cockpit learns about the new case from the cockpit adapter, like about every other case.
   *
   * <p>
   * The generated class does not check its values. The OpenAPI says that the applicant must
   * not be empty and that the amount must be positive, so this method refuses anything else with
   * 400, before a process is started. The OpenAPI answers 201. The generated interface does not
   * say so, so this method carries {@code @ResponseStatus} itself. Quarkus reads it only from the
   * method which declares the HTTP method, so the method repeats {@code @POST} of the
   * interface.
   * </p>
   *
   * @param loanRequest What the user typed into the start form.
   * @return The id of the new loan request.
   */
  @Override
  @POST
  @ResponseStatus(201)
  public StartedLoanRequest requestLoan(
      final LoanRequest loanRequest) {

    if ((loanRequest.getApplicant() == null) || loanRequest
        .getApplicant()
        .isBlank()) {
      throw new WebApplicationException("The applicant is missing", Status.BAD_REQUEST);
    }
    if ((loanRequest.getAmount() == null) || (loanRequest.getAmount() < 1)) {
      throw new WebApplicationException("The amount has to be a positive number", Status.BAD_REQUEST);
    }

    final var loanRequestId = loanApproval.request(loanRequest
        .getApplicant()
        .strip(), loanRequest.getAmount());

    return new StartedLoanRequest(loanRequestId);

  }

}
