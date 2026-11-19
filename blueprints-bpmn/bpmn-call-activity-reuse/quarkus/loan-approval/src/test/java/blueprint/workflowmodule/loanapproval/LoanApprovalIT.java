package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.vanillabp.spi.process.WorkflowNotFoundException;
import jakarta.inject.Inject;

/**
 * The test of the first process which uses the credit check. It starts a loan approval, waits
 * until the credit check it asked for waits for the agency, lets the agency answer, and waits
 * for the decision.
 *
 * <p>
 * What is asserted is the workflow aggregates, never the engine.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  blueprint.workflowmodule.loanapproval.creditcheck.Service creditCheck;

  @Inject
  blueprint.workflowmodule.loanapproval.creditcheck.model.AggregateRepository creditChecks;

  /**
   * Starts a loan approval and waits until its credit check waits for the agency.
   *
   * @return The id of the loan request.
   */
  private String startAndAwaitTheCreditCheck() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getCreditCheckId() != null);

    awaitAggregate(
        creditChecks::findByIdOptional,
        loanRequest.getCreditCheckId(),
        aggregate -> Boolean.TRUE.equals(aggregate.getAgencyAsked()));

    return loanRequestId;

  }

  /**
   * The agency answers, retrying while the BPMS does not know the credit check yet. A remote
   * engine finds a workflow by an index it fills in the background. An embedded engine answers
   * at once, and the loop runs once.
   *
   * @param loanRequestId The id of the loan request whose credit check is answered.
   * @param score         The score the agency gives.
   */
  private void agencyAnswers(
      final String loanRequestId,
      final int score) {

    final var creditCheckId = loanApprovals
        .findByIdOptional(loanRequestId)
        .orElseThrow()
        .getCreditCheckId();

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreException(WorkflowNotFoundException.class)
        .until(() -> {
          creditCheck.agencyAnswered(creditCheckId, score);
          return true;
        });

  }

  @Test
  @DisplayName("The loan approval waits while the credit check runs")
  public void theLoanApprovalWaitsForTheCreditCheck() {

    final var loanRequestId = startAndAwaitTheCreditCheck();

    // the service task behind the message event has not run
    final var loanRequest = loanApprovals.findByIdOptional(loanRequestId).orElseThrow();
    assertThat(loanRequest.getCreditScore()).isNull();
    assertThat(loanRequest.getApproved()).isNull();

    // the credit check has an aggregate of its own, which knows who asked
    final var check = creditChecks.findByIdOptional(loanRequest.getCreditCheckId()).orElseThrow();
    assertThat(check.getRequestedBy()).isEqualTo(Service.CREDIT_CHECK_REQUESTER);
    assertThat(check.getCaseId()).isEqualTo(loanRequestId);
    assertThat(check.getScore()).isNull();

  }

  @Test
  @DisplayName("The result of the credit check lets the loan approval continue")
  public void theResultLetsTheLoanApprovalContinue() {

    final var loanRequestId = startAndAwaitTheCreditCheck();

    agencyAnswers(loanRequestId, 70);

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getApproved() != null);

    assertThat(loanRequest.getCreditScore()).isEqualTo(70);
    assertThat(loanRequest.getApproved()).isTrue();

    final var check = creditChecks.findByIdOptional(loanRequest.getCreditCheckId()).orElseThrow();
    assertThat(check.getReported()).isTrue();

  }

  @Test
  @DisplayName("A low score rejects the loan")
  public void aLowScoreRejectsTheLoan() {

    final var loanRequestId = startAndAwaitTheCreditCheck();

    agencyAnswers(loanRequestId, 30);

    final var loanRequest = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getApproved() != null);

    assertThat(loanRequest.getApproved()).isFalse();

  }

}
