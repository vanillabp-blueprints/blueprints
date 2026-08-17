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
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser - no tooling, no request bodies.
 *
 * <p>
 * The endpoint answering the partner is the one that matters here: it opens the transaction
 * of the application, and it may run while the BPMS is inside the transaction of the other
 * branch.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/loan-approval")
public class ApiController {

  @Inject
  Service service;

  /**
   * Starts a loan approval. This is the one URL the README names.
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
   * Answers the open partner task, which lets that branch continue.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param approvedBy    Who approved.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/{loanRequestId}/approve")
  public String approve(
      @PathParam("loanRequestId") final String loanRequestId,
      @QueryParam("approvedBy")
      @DefaultValue("partner") final String approvedBy) {

    service.approvePartnerRequest(loanRequestId, approvedBy);

    return "The partner approved loan approval '"
        + loanRequestId
        + "'";

  }

  /**
   * Says that the documents arrived, which lets the other branch read them.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/{loanRequestId}/documents-ready")
  public String documentsReady(
      @PathParam("loanRequestId") final String loanRequestId) {

    service.documentsReady(loanRequestId);

    return "The documents of loan approval '"
        + loanRequestId
        + "' arrived";

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
