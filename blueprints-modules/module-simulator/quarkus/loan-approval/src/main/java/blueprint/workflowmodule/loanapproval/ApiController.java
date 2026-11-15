package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
import java.util.UUID;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import blueprint.externalsystems.creditrating.CreditRatingUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser. No tooling, no request bodies.
 *
 * <p>
 * It talks to {@link Service} and to nothing else. When a system around the module does not
 * answer while a user waits, the user gets a 503 which names the role of that system.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/loan-approval")
public class ApiController {

  /**
   * The answer to a loan request.
   *
   * @param loanRequestId The id of the loan request.
   * @param outcome       {@code started} or {@code declined}.
   */
  public record Answer(
                       String loanRequestId,
                       String outcome) {
  }

  /**
   * The answer when a system around the module does not answer, in the fields of a problem
   * detail (RFC 9457). It is a record of its own, so the JSON is the same whatever library the
   * application uses to write problem details.
   *
   * @param status         The HTTP status, 503.
   * @param title          A short summary.
   * @param detail         What the user can do.
   * @param externalSystem The role of the system which does not answer.
   */
  public record Unavailable(
                            int status,
                            String title,
                            String detail,
                            String externalSystem) {
  }

  /** The media type of a problem detail. */
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

  @Inject
  Service loanApproval;

  /**
   * Requests a loan. This is the URL the README names.
   *
   * @param customerId The customer asking for the loan.
   * @param amount     The amount requested.
   * @return Whether a workflow started for the loan request.
   */
  @GET
  @Path("/request")
  public Answer request(
      @QueryParam("customerId")
      @DefaultValue("C-1001") final String customerId,
      @QueryParam("amount")
      @DefaultValue("5000") final int amount) {

    final var loanRequestId = UUID
        .randomUUID()
        .toString();

    if (!loanApproval.request(loanRequestId, customerId, amount)) {
      return new Answer(loanRequestId, "declined");
    }

    log.info(
        "Show the loan approval -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return new Answer(loanRequestId, "started");

  }

  /**
   * Completes the user task "Prepare contract".
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task, as the log and the cockpit show it.
   * @param interestRate  The interest rate of the contract, in percent.
   * @return A confirmation.
   */
  @GET
  @Path("/{loanRequestId}/prepare-contract/{taskId}")
  public String prepareContract(
      @PathParam("loanRequestId") final String loanRequestId,
      @PathParam("taskId") final String taskId,
      @QueryParam("interestRate") final BigDecimal interestRate) {

    loanApproval.prepareContract(loanRequestId, taskId, interestRate);

    return "Contract of loan approval '"
        + loanRequestId
        + "' prepared";

  }

  /**
   * Shows what the process did.
   *
   * @param loanRequestId The id returned by the loan request.
   * @return The workflow aggregate as it is stored right now.
   */
  @GET
  @Path("/{loanRequestId}")
  public String show(
      @PathParam("loanRequestId") final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(Object::toString)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

  /**
   * The credit rating does not answer while the user waits for it. The answer says which system
   * is missing, by its role, so the user knows it is not the application which failed. The
   * product behind the role stays inside the adapter.
   *
   * @param e What the adapter reported.
   * @return A problem detail with status 503.
   */
  @ServerExceptionMapper(CreditRatingUnavailableException.class)
  public RestResponse<Unavailable> creditRatingUnavailable(
      final CreditRatingUnavailableException e) {

    log.warn("{}", e.getMessage());

    return RestResponse.ResponseBuilder
        .create(
            RestResponse.Status.SERVICE_UNAVAILABLE,
            new Unavailable(
                RestResponse.Status.SERVICE_UNAVAILABLE
                    .getStatusCode(), "Service Unavailable", "The credit rating does not answer. Try again later.", CreditRatingUnavailableException.ROLE))
        .type(MediaType.valueOf(APPLICATION_PROBLEM_JSON))
        .build();

  }

}
