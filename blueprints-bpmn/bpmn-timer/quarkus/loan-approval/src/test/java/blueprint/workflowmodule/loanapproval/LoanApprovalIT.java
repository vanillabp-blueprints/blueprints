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
 * Every test waits for the aggregate rather than for a clock. A timer is the BPMS' business
 * and it fires when it fires; what the test asserts is what the workflow did about it.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

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

    loanApproval.request(loanRequestId, 5000);

    // the timer in the sequence flow delays this: the request goes out AFTER the
    // cool-off period, not when the workflow started
    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        SCHEDULER_TIMEOUT,
        aggregate -> aggregate.getPartnerApprovalTaskId() != null)
        .getPartnerApprovalTaskId();

  }

  @Test
  @DisplayName("An answer before the deadline completes the task")
  public void anAnswerInTimeIsAccepted() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    loanApproval.partnerApproved(loanRequestId, taskId);

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanRequest.getPartnerApproved()).isTrue();
    assertThat(loanRequest.getTimedOut()).isNull();

  }

  @Test
  @DisplayName("Without an answer the deadline takes the task away")
  public void theDeadlineEndsTheWait() {

    final var loanRequestId = UUID.randomUUID().toString();

    startAndAwaitPartnerRequest(loanRequestId);

    // nobody answers: the timer boundary event fires, the workflow leaves the task and
    // takes the path behind the timer
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        SCHEDULER_TIMEOUT,
        aggregate -> Boolean.TRUE.equals(aggregate.getTimedOut()));

    assertThat(loanRequest.getCustomerInformed()).isNull();

    // The module says three days. The test configuration shortened the deadline, and the
    // aggregate copied that value when the loan was requested. The model read it from there.
    assertThat(loanRequest.getPartnerDeadline()).isEqualTo("PT3S");

    if (System.getProperty("blueprint.bpms", "").matches("camunda7|operaton|cibseven")) {
      // The handler heard about it: an interrupting boundary event cancels the task, and
      // the stored id no longer leads anywhere. Not every BPMS reports that - Camunda 8
      // does not tell a worker that its job was canceled - so an application must not
      // depend on it for correctness. Completing a task that is gone is a no-op anyway.
      assertThat(loanRequest.getPartnerApprovalTaskId()).isNull();
    }

  }

  @Test
  @DisplayName("A withdrawal during the cool-off period reaches the gateway behind it")
  public void aWithdrawalDuringTheCoolOffPeriodIsSeenByTheGateway() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    // The rating runs before the cool-off period. Once it is stored, the workflow waits for
    // its timer, and the BPMS holds 'withdrawn' as false. No sync point of VanillaBP comes
    // until the timer fires.
    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    loanApproval.withdraw(loanRequestId);

    // The gateway behind the timer takes the withdrawn branch only if the change reached
    // the BPMS. Without aggregateChanged it would read false and ask the partner.
    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        SCHEDULER_TIMEOUT,
        aggregate -> Boolean.TRUE.equals(aggregate.getWithdrawalConfirmed()));

    assertThat(loanRequest.getPartnerApprovalTaskId()).isNull();
    assertThat(partner.invocations())
        .describedAs("the partner is not asked about a withdrawn request")
        .isEmpty();

  }

}
