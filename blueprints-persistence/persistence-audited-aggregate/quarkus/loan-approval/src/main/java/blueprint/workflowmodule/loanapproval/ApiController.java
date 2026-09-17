package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import blueprint.workflowmodule.loanapproval.audit.ChangeAuthor;
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
 * It talks to {@link Service} and to nothing else, and it says who is acting while it
 * does. The name comes in with the request because this blueprint has nobody logged in;
 * an application with a security framework reads the authenticated user, in a filter, and
 * then the API has nothing to do with the auditing either. What matters is where it sits:
 * around the call which opens the transaction, because Envers writes the revision when
 * that transaction commits.
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
   * process are logged once the risk assessment is open.
   *
   * @param amount      The amount requested.
   * @param requestedBy Who asks for the loan, which is what the trail will name.
   * @return The id of the loan request started.
   */
  @GET
  @Path("/start")
  public String start(
      @QueryParam("amount")
      @DefaultValue("5000") final int amount,
      @QueryParam("requestedBy")
      @DefaultValue("the customer") final String requestedBy) {

    final var loanRequestId = UUID.randomUUID().toString();

    ChangeAuthor.attributeTo(
        requestedBy,
        () -> service.initiateLoanApproval(loanRequestId, amount, requestedBy));

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * Answers the open risk assessment, which completes the user task and lets the process
   * continue to the payout.
   *
   * @param loanRequestId    The id returned by starting the process.
   * @param taskId           The id of the user task, taken from the logged URL.
   * @param riskIsAcceptable What the assessment concluded.
   * @param decidedBy        Who assessed it, which is what the trail will name.
   * @return What was done, for the browser to show.
   */
  @GET
  @Path("/{loanRequestId}/assess-risk/{taskId}")
  public String assessRisk(
      @PathParam("loanRequestId") final String loanRequestId,
      @PathParam("taskId") final String taskId,
      @QueryParam("riskIsAcceptable")
      @DefaultValue("true") final boolean riskIsAcceptable,
      @QueryParam("decidedBy")
      @DefaultValue("paula") final String decidedBy) {

    ChangeAuthor.attributeTo(
        decidedBy,
        () -> service.assessRisk(loanRequestId, taskId, riskIsAcceptable, decidedBy));

    return "The risk of loan approval '"
        + loanRequestId
        + "' was assessed";

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

  /**
   * Shows every state the loan approval went through and who caused it. The answer of
   * the auditing to the question this blueprint is about.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The trail, oldest change first.
   */
  @GET
  @Path("/{loanRequestId}/trail")
  public String trail(
      @PathParam("loanRequestId") final String loanRequestId) {

    final var trail = service.getTrail(loanRequestId);

    return trail.isEmpty()
        ? "unknown loan request '"
            + loanRequestId
            + "'"
        : String.join("\n", trail);

  }

}
