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
 * The integration test of this workflow module: it starts a real workflow in a real BPMS,
 * waits for the process to have asked the partner, answers as the partner would and waits
 * again.
 *
 * <p>
 * One test per way an asynchronous task ends, because the outcomes are the aspect of this
 * blueprint. Each of them asserts on the workflow aggregate, never on the engine.
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

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getPartnerApprovalTaskId() != null)
        .getPartnerApprovalTaskId();

  }

  @Test
  @DisplayName("The task stays open, and the partner was asked once")
  public void theTaskWaitsForThePartner() {

    final var loanRequestId = UUID.randomUUID().toString();

    startAndAwaitPartnerRequest(loanRequestId);

    partner.awaitInvocation("approve "
        + loanRequestId);

    // the service task behind the waiting one did not run: returning from the handler
    // did not complete the task
    final var loanApproval = loanApprovals.findByIdOptional(loanRequestId).orElseThrow();
    assertThat(loanApproval.getCustomerInformed()).isNull();
    assertThat(loanApproval.getCreditRating()).isEqualTo(50);

  }

  @Test
  @DisplayName("The partner's approval completes the task and the workflow continues")
  public void approvalCompletesTheTask() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    service.partnerDecided(loanRequestId, taskId, true);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproved()).isTrue();
    assertThat(loanApproval.getRejected()).isNull();
    assertThat(loanApproval.getPartnerApprovalTaskId()).isNull();

  }

  @Test
  @DisplayName("The partner's refusal cancels the task and the workflow takes the error path")
  public void refusalCancelsTheTask() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    service.partnerDecided(loanRequestId, taskId, false);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getRejected()));

    assertThat(loanApproval.getPartnerApproved()).isFalse();
    assertThat(loanApproval.getCustomerInformed()).isNull();

  }

}
