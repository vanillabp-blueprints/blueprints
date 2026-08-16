package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work.
 *
 * <p>
 * One test per outcome a service task can have, because the outcomes are the aspect of
 * this blueprint. Each of them asserts on the workflow aggregate, never on the engine.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  CreditRatingSimulator creditRatings;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    creditRatings.reset();

  }

  @Test
  public void theServiceTaskRatesTheLoanAndPassesTheProviderFromTheBpmn() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    // Nobody compiled the provider in: it comes from the input mapping of the BPMN task.
    assertThat(loanApproval.getRatedBy()).isEqualTo("acme-rating");
    assertThat(loanApproval.getRejectionReason()).isNull();

  }

  @Test
  public void aProviderNotAnsweringMakesTheBpmsDeliverTheTaskAgain() {

    creditRatings.failNextRequests(1);

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    // The first request was refused, so the provider was asked more than once.
    assertThat(creditRatings.invocations()).hasSizeGreaterThan(1);

  }

  @Test
  public void aRejectedLoanTakesTheErrorPathAndKeepsWhatTheHandlerWrote() {

    final var loanRequestId = UUID.randomUUID().toString();

    // 500 / 100 is a rating of 5, below the configured minimum of 10.
    service.initiateLoanApproval(loanRequestId, 500);

    final var loanApproval = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getRejectionReason() != null);

    // The handler threw a TaskException, and everything it wrote before is still here.
    assertThat(loanApproval.getCreditRating()).isEqualTo(5);
    assertThat(loanApproval.getRejectionReason()).contains("below the minimum");

  }

}
