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
 * The endpoints are the two wait states of the process plus the two reads. Which BPMS runs
 * the workflow behind them is never a parameter, and that is what a migration looks like
 * from the outside: nothing.
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
   * Answers the risk assessment, which is the user task the process waits at.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, logged when it was created.
   * @return What happened.
   */
  @GET
  @Path("/{loanRequestId}/assess-risk/{taskId}")
  public String assessRisk(
      @PathParam("loanRequestId") final String loanRequestId,
      @PathParam("taskId") final String taskId) {

    service.assessRisk(loanRequestId, taskId);

    return "risk of '"
        + loanRequestId
        + "' assessed";

  }

  /**
   * Reports the signed contract, which is the message the process waits for.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param signedBy      Who signed.
   * @return What happened.
   */
  @GET
  @Path("/{loanRequestId}/contract-signed")
  public String contractSigned(
      @PathParam("loanRequestId") final String loanRequestId,
      @QueryParam("signedBy")
      @DefaultValue("Jane Doe") final String signedBy) {

    service.contractSigned(loanRequestId, signedBy);

    return "contract of '"
        + loanRequestId
        + "' signed by "
        + signedBy;

  }

  /**
   * Shows which BPMS runs this workflow, which is what this blueprint is about.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The adapter id of the BPMS holding the workflow.
   */
  @GET
  @Path("/{loanRequestId}/bpms")
  public String bpms(
      @PathParam("loanRequestId") final String loanRequestId) {

    return service
        .bpmsHolding(loanRequestId)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

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
