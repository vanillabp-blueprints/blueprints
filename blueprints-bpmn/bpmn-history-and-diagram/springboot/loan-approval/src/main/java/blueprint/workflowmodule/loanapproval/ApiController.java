package blueprint.workflowmodule.loanapproval;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.vanillabp.spi.process.ProcessDefinition;
import io.vanillabp.spi.process.WorkflowHistory;
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
@RestController
@RequestMapping("/api/loan-approval")
public class ApiController {

  @Autowired
  private Service service;

  /**
   * Starts a loan approval. This is the one URL the README names.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @GetMapping("/start")
  public String start(
      @RequestParam(defaultValue = "5000") final int amount) {

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
  @GetMapping("/{loanRequestId}/approve")
  public String approve(
      @PathVariable final String loanRequestId) {

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
  @GetMapping("/{loanRequestId}")
  public String show(
      @PathVariable final String loanRequestId) {

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
  @GetMapping("/{loanRequestId}/definitions")
  public List<ProcessDefinition> definitions(
      @PathVariable final String loanRequestId,
      @RequestParam(required = false) final String historyContext) {

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
  @GetMapping(value = "/{loanRequestId}/diagram", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<InputStreamResource> diagram(
      @PathVariable final String loanRequestId,
      @RequestParam(required = false) final String processDefinitionId) {

    final var definitionId = processDefinitionId != null
        ? processDefinitionId
        : service
            .getProcessDefinitions(loanRequestId, null)
            .getFirst()
            .id();

    return ResponseEntity
        .ok(new InputStreamResource(service.getBpmnXml(definitionId)));

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
  @GetMapping("/{loanRequestId}/history")
  public WorkflowHistory history(
      @PathVariable final String loanRequestId,
      @RequestParam(required = false) final String historyContext) {

    return service.getWorkflowHistory(loanRequestId, historyContext);

  }

}
