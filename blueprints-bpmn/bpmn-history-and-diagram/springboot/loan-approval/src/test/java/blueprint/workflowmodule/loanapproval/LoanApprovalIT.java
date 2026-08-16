package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.process.WorkflowElementHistory;
import io.vanillabp.spi.process.WorkflowHistory;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and asks that BPMS what a viewer would ask.
 *
 * <p>
 * What a BPMS records differs, and the assertions are written accordingly: that the
 * elements this test drove through appear, not that the history holds exactly them. An
 * engine which also records gateways and flow nodes is not wrong, and a test saying so
 * would fail on the next BPMS for no reason.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  private String startedWorkflow() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getPartnerApprovalTaskId() != null);

    return loanRequestId;

  }

  /**
   * The history of a REMOTE BPMS is fed by an exporter, so it lags behind what the engine
   * already did. Waiting for the element rather than asserting immediately is what makes
   * this test the same test on every BPMS.
   */
  private WorkflowHistory awaitHistory(
      final String loanRequestId,
      final Predicate<WorkflowHistory> condition) {

    WorkflowHistory history = null;
    final var deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      history = service.getWorkflowHistory(loanRequestId, null);
      if ((history != null) && condition.test(history)) {
        return history;
      }
      try {
        Thread.sleep(250);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
    throw new AssertionError("The history of workflow '"
        + loanRequestId
        + "' did not reach the expected state within "
        + TIMEOUT
        + ". Last seen: "
        + history);

  }

  private static List<String> elementIds(
      final WorkflowHistory history) {

    return history
        .elementsHistory()
        .stream()
        .map(WorkflowElementHistory::elementId)
        .toList();

  }

  @Test
  @DisplayName("the definitions name the process and the diagram is its BPMN")
  public void theDiagramIsServed() throws IOException {

    final var loanRequestId = startedWorkflow();

    final var definitions = service.getProcessDefinitions(loanRequestId, null);
    assertThat(definitions)
        .describedAs("a process without call activities uses exactly one definition")
        .hasSize(1);
    assertThat(definitions.getFirst().usedByElements())
        .describedAs("null marks the definition the workflow itself runs on")
        .isNull();
    assertThat(definitions.getFirst().id())
        .describedAs("an opaque id, to be passed back unchanged")
        .isNotBlank();

    final String xml;
    try (var stream = service.getBpmnXml(definitions.getFirst().id())) {
      xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    // element ids are never scoped by VanillaBP, unlike the BPMN process id
    assertThat(xml)
        .describedAs("the BPMN the workflow runs on, as the BPMS holds it")
        .contains("ServiceTask_RetrieveCreditRating")
        .contains("SendTask_AwaitPartnerApproval");

  }

  @Test
  @DisplayName("a running workflow reports where it stands")
  public void theHistoryOfARunningWorkflow() {

    final var loanRequestId = startedWorkflow();

    final var history = awaitHistory(
        loanRequestId,
        candidate -> (candidate.elementsHistory() != null) && elementIds(candidate)
            .contains("SendTask_AwaitPartnerApproval"));

    assertThat(history.startTime()).isNotNull();
    assertThat(history.endTime())
        .describedAs("the workflow is still waiting")
        .isNull();
    assertThat(elementIds(history))
        .describedAs("the task which already ran")
        .contains("ServiceTask_RetrieveCreditRating");

    final var waiting = history
        .elementsHistory()
        .stream()
        .filter(element -> "SendTask_AwaitPartnerApproval".equals(element.elementId()))
        .findFirst()
        .orElseThrow();
    assertThat(waiting.startTime()).isNotNull();
    assertThat(waiting.endTime())
        .describedAs("the element the workflow stands at has no end time - this is what a viewer colours")
        .isNull();
    assertThat(waiting.isCanceled()).isFalse();

  }

  @Test
  @DisplayName("an ended workflow reports what it did")
  public void theHistoryOfAnEndedWorkflow() {

    final var loanRequestId = startedWorkflow();

    service.partnerApproved(loanRequestId);

    final Aggregate decided = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getDecision() != null);
    assertThat(decided.getDecision()).isEqualTo("approved");

    final var history = awaitHistory(
        loanRequestId,
        candidate -> (candidate.endTime() != null) && elementIds(candidate)
            .contains("ServiceTask_DecideOnLoan"));

    assertThat(elementIds(history))
        .describedAs("every task the workflow ran through")
        .contains(
            "ServiceTask_RetrieveCreditRating",
            "SendTask_AwaitPartnerApproval",
            "ServiceTask_DecideOnLoan");
    assertThat(history.elementsHistory())
        .describedAs("nothing was canceled and nothing is in error")
        .allSatisfy(element -> {
          assertThat(element.isCanceled()).isFalse();
          assertThat(element.error()).isNull();
        });

  }

}
