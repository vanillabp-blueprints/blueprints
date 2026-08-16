package blueprint.workflowmodule.loanapproval;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.WorkflowHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be walked
 * through in a browser - no tooling, no request bodies.
 *
 * <p>
 * The three viewing endpoints are what a UI would call: the definitions to know what to
 * draw, the BPMN XML to draw it, and the history to colour it. A browser shows all three
 * as they are, which is the whole demonstration this blueprint can give without a UI.
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

    return loanRequestId;

  }

  /**
   * Moves the waiting workflow on.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What happened.
   */
  @GET
  @Path("/{loanRequestId}/approve")
  public String approve(
      @PathParam("loanRequestId") final String loanRequestId) {

    service.partnerApproved(loanRequestId);

    return "the partner approved loan request '"
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

  /**
   * The process definitions the workflow uses. A viewer starts here: it needs an id before
   * it can ask for a diagram.
   *
   * @param loanRequestId  The id returned by starting the process.
   * @param historyContext The context of a call activity, or nothing for the workflow
   *                       itself.
   * @return The definitions.
   */
  @GET
  @Path("/{loanRequestId}/definitions")
  public List<ProcessDefinition> definitions(
      @PathParam("loanRequestId") final String loanRequestId,
      @QueryParam("historyContext") final String historyContext) {

    return service.getProcessDefinitions(loanRequestId, historyContext);

  }

  /**
   * The BPMN XML of the workflow's process, or of one of the definitions listed by
   * {@link #definitions}.
   *
   * @param loanRequestId       The id returned by starting the process.
   * @param processDefinitionId The definition to show; defaults to the one the workflow
   *                            runs on.
   * @return The BPMN XML.
   */
  @GET
  @Path("/{loanRequestId}/diagram")
  @Produces(MediaType.APPLICATION_XML)
  public InputStream diagram(
      @PathParam("loanRequestId") final String loanRequestId,
      @QueryParam("processDefinitionId") final String processDefinitionId) {

    final var definitionId = processDefinitionId != null
        ? processDefinitionId
        : service
            .getProcessDefinitions(loanRequestId, null)
            .getFirst()
            .id();

    return service.getBpmnXml(definitionId);

  }

  /**
   * What the workflow has done so far: the elements it passed, in execution order, with
   * the times a viewer colours by.
   *
   * @param loanRequestId  The id returned by starting the process.
   * @param historyContext The context of a call activity, or nothing for the workflow
   *                       itself.
   * @return The history.
   */
  @GET
  @Path("/{loanRequestId}/history")
  public WorkflowHistory history(
      @PathParam("loanRequestId") final String loanRequestId,
      @QueryParam("historyContext") final String historyContext) {

    return service.getWorkflowHistory(loanRequestId, historyContext);

  }

}
