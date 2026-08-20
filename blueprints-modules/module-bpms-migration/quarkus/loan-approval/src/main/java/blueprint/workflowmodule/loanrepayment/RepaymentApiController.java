package blueprint.workflowmodule.loanrepayment;

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
 * The API of the repayment use case, GET only like every other one in this repository.
 */
@Slf4j
@ApplicationScoped
@Path("/api/loan-repayment")
public class RepaymentApiController {

  @Inject
  RepaymentService service;

  /**
   * Starts a repayment, the workflow which stays in the old BPMS.
   *
   * @param amount The amount owed.
   * @return The id of the repayment started.
   */
  @GET
  @Path("/start")
  public String start(
      @QueryParam("amount")
      @DefaultValue("500") final int amount) {

    final var repaymentId = UUID.randomUUID().toString();

    service.initiateRepayment(repaymentId, amount);

    log.info(
        "Which BPMS runs it -> http://localhost:8080/api/loan-repayment/{}/bpms",
        repaymentId);

    return repaymentId;

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The adapter id of the BPMS holding the workflow.
   */
  @GET
  @Path("/{repaymentId}/bpms")
  public String bpms(
      @PathParam("repaymentId") final String repaymentId) {

    return service
        .bpmsHolding(repaymentId)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

  /**
   * @param repaymentId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @GET
  @Path("/{repaymentId}")
  public String show(
      @PathParam("repaymentId") final String repaymentId) {

    return service
        .getRepayment(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
