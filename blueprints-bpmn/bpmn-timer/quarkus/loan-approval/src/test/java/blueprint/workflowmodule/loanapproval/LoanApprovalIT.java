package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and lets the timers of the model decide what happens.
 *
 * <p>
 * Both tests wait for the aggregate rather than for a clock. A timer is the BPMS' business
 * and it fires when it fires; what the test asserts is what the workflow did about it.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  PartnerApprovalSimulator partner;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    partner.reset();

  }

  private String startAndAwaitPartnerRequest(
      final String loanRequestId) {

    service.initiateLoanApproval(loanRequestId, 5000);

    // the timer in the sequence flow delays this: the request goes out AFTER the
    // cool-off period, not when the workflow started
    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getPartnerApprovalTaskId() != null)
        .getPartnerApprovalTaskId();

  }

  @Test
  @DisplayName("An answer before the deadline completes the task")
  public void anAnswerInTimeIsAccepted() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    service.partnerApproved(loanRequestId, taskId);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproved()).isTrue();
    assertThat(loanApproval.getTimedOut()).isNull();

  }

  @Test
  @DisplayName("Without an answer the deadline takes the task away")
  public void theDeadlineEndsTheWait() {

    final var loanRequestId = UUID.randomUUID().toString();

    startAndAwaitPartnerRequest(loanRequestId);

    // nobody answers: the timer boundary event fires, the workflow leaves the task and
    // takes the path behind the timer
    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getTimedOut()));

    assertThat(loanApproval.getCustomerInformed()).isNull();

    if ("camunda7".equals(System.getProperty("blueprint.bpms"))) {
      // The handler heard about it: an interrupting boundary event cancels the task, and
      // the stored id no longer leads anywhere. Not every BPMS reports that - Camunda 8
      // does not tell a worker that its job was canceled - so an application must not
      // depend on it for correctness. Completing a task that is gone is a no-op anyway.
      assertThat(loanApproval.getPartnerApprovalTaskId()).isNull();
    }

  }

}
