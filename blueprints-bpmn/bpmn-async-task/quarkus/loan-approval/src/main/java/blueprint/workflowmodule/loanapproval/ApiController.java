package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be
 * walked through in a browser - no tooling, no request bodies.
 *
 * <p>
 * The endpoint carrying the partner's answer is the callback a real partner would call.
 * That it happens to be a browser here changes nothing about its shape: an id, an answer,
 * and no knowledge of the BPMS.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/loan-approval")
public class ApiController {

  @Inject
  Service service;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs carrying the
   * partner's answer are logged once the request went out.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @GET
  @Path("/start")
  public String start(
      @QueryParam("amount")
      @DefaultValue("5000") final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * The partner's answer, which completes the waiting task or cancels it by BPMN error.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the waiting task, taken from the logged URL.
   * @param approved      What the partner decided.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/{loanRequestId}/partner-decision/{taskId}")
  public String partnerDecision(
      @PathParam("loanRequestId") final String loanRequestId,
      @PathParam("taskId") final String taskId,
      @QueryParam("approved")
      @DefaultValue("true") final boolean approved) {

    service.partnerDecided(loanRequestId, taskId, approved);

    return "The partner "
        + (approved ? "approved" : "rejected")
        + " loan approval '"
        + loanRequestId
        + "'";

  }

  /**
   * Shows what the process did, which is the second half of operating it in a browser.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @GET
  @Path("/{loanRequestId}")
  public String show(
      @PathParam("loanRequestId") final String loanRequestId) {

    return service
        .getLoanApproval(loanRequestId)
        .map(Object::toString)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

}
