package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import blueprint.workflowmodule.loanapproval.model.Check;
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
 * implemented by a BPMN process is none of its business, and what arrives here is a list of
 * checks rather than a list of BPMN elements.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/loan-approval")
public class ApiController {

  @Inject
  Service service;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs continuing the
   * process are logged once a case worker has to pick.
   *
   * @param amount The amount requested. Above ten thousand a case worker picks the checks,
   *               below it the decision table does.
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
   * Picks the additional checks, which completes the user task and lets the process enter
   * the ad-hoc subprocess.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the user task, taken from the logged URL.
   * @param checks        The checks to run, comma separated: income, fraud, collateral.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/{loanRequestId}/select-checks/{taskId}")
  public String selectChecks(
      @PathParam("loanRequestId") final String loanRequestId,
      @PathParam("taskId") final String taskId,
      @QueryParam("checks")
      @DefaultValue("income,fraud") final String checks) {

    service.selectChecks(loanRequestId, taskId, Check.of(checks.split(",")));

    return "Loan approval '"
        + loanRequestId
        + "' runs the checks "
        + checks;

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
