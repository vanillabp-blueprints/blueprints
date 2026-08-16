package blueprint.workflowmodule.loanapproval;

import java.math.BigDecimal;
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
 * It talks to {@link Service} and to nothing else. That the use case happens to be
 * implemented by a BPMN process is none of its business.
 * </p>
 *
 * <p>
 * Notice the path publishing the rate: it carries no loan request id, while every other
 * endpoint does. That is the signal showing through the API - the news is not about one
 * business case, and there is nothing to address it to.
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
   * Publishes today's interest rate, which broadcasts the signal. Whoever waits for it at
   * that moment continues, and the caller never learns who that was.
   *
   * @param rate The interest rate, in percent.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/publish-interest-rate")
  public String publishInterestRate(
      @QueryParam("rate")
      @DefaultValue("3.5") final BigDecimal rate) {

    service.publishInterestRate(rate);

    return "An interest rate of "
        + rate
        + "% was published. Every loan approval waiting for it continues; one arriving later"
        + " waits for the next publication, because a signal is not buffered.";

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
