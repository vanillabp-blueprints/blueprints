package blueprint.workflowmodule.loanapproval;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.WorkflowHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-approval.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements LoanApprovalApi {

  @Inject
  Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL the README names.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final Integer amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    return loanRequestId;

  }

  /**
   * Moves the waiting workflow on.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return What happened.
   */
  @Override
  public String approve(
      final String loanRequestId) {

    loanApproval.partnerApproved(loanRequestId);

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
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
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
  @Override
  public List<ProcessDefinition> definitions(
      final String loanRequestId,
      final String historyContext) {

    return loanApproval.getProcessDefinitions(loanRequestId, historyContext);

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
  @Override
  public InputStream diagram(
      final String loanRequestId,
      final String processDefinitionId) {

    final var definitionId = processDefinitionId != null
        ? processDefinitionId
        : loanApproval
            .getProcessDefinitions(loanRequestId, null)
            .getFirst()
            .id();

    return loanApproval.getBpmnXml(definitionId);

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
  @Override
  public WorkflowHistory history(
      final String loanRequestId,
      final String historyContext) {

    return loanApproval.getWorkflowHistory(loanRequestId, historyContext);

  }

}
