package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.vanillabp.spi.process.WorkflowNotFoundException;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS,
 * waits for it to have reached the message event, correlates the message and waits again.
 *
 * <p>
 * What is asserted is the workflow aggregate, never the engine - a waiting workflow shows
 * itself by what has NOT happened yet.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  private String startAndAwaitTheWaitingWorkflow() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    return loanRequestId;

  }

  /**
   * Correlates the message, retrying while the BPMS does not know the workflow yet.
   *
   * <p>
   * A remote engine answers "which workflow belongs to this aggregate?" from an index it
   * fills asynchronously, so a message arriving seconds after the workflow started may
   * find nothing although the workflow is there. An embedded engine answers immediately
   * and this loop runs once. Production traffic rarely notices - a signed contract does
   * not come back within a second - but a test does, and so would a process whose message
   * follows right after the start.
   * </p>
   *
   * @param loanRequestId The natural id of the loan request.
   * @param signedBy      Who signed the contract.
   */
  private void correlateAsSoonAsTheWorkflowIsVisible(
      final String loanRequestId,
      final String signedBy) {

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreException(WorkflowNotFoundException.class)
        .until(() -> {
          service.contractSigned(loanRequestId, signedBy);
          return true;
        });

  }

  @Test
  @DisplayName("The workflow waits for the message")
  public void theWorkflowWaitsForTheMessage() {

    final var loanRequestId = startAndAwaitTheWaitingWorkflow();

    // the service task behind the message event has not run, so the workflow is standing
    // at the catch event rather than having passed it
    final var loanApproval = loanApprovals.findById(loanRequestId).orElseThrow();
    assertThat(loanApproval.getPaidOut()).isNull();
    assertThat(loanApproval.getContractSignedBy()).isNull();

  }

  @Test
  @DisplayName("Correlating the message lets the workflow continue")
  public void theMessageLetsTheWorkflowContinue() {

    final var loanRequestId = startAndAwaitTheWaitingWorkflow();

    correlateAsSoonAsTheWorkflowIsVisible(loanRequestId, "Jane Doe");

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

    // what the message carried is on the aggregate, which is where the task behind the
    // message event read it from
    assertThat(loanApproval.getContractSignedBy()).isEqualTo("Jane Doe");

  }

  @Test
  @DisplayName("The same message arriving twice is refused by the application")
  public void aRepeatedMessageIsRefused() {

    final var loanRequestId = startAndAwaitTheWaitingWorkflow();

    correlateAsSoonAsTheWorkflowIsVisible(loanRequestId, "Jane Doe");
    awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

    // VanillaBP does not deduplicate a message without a correlation id, so this is the
    // application's decision - and it keeps the second delivery away from the BPMS, where
    // nothing waits for it any more
    service.contractSigned(loanRequestId, "Somebody Else");

    final var loanApproval = loanApprovals.findById(loanRequestId).orElseThrow();
    assertThat(loanApproval.getContractSignedBy()).isEqualTo("Jane Doe");

  }

}
