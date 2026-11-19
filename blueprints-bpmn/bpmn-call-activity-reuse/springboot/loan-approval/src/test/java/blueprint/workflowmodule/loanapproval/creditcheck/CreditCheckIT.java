package blueprint.workflowmodule.loanapproval.creditcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.creditcheck.model.AggregateRepository;
import io.vanillabp.spi.process.WorkflowNotFoundException;

/**
 * The test of the reused process itself: two processes use it at the same time, and each one
 * gets its own result back.
 */
public class CreditCheckIT extends WorkflowModuleTest {

  @Autowired
  private Service creditCheck;

  @Autowired
  private AggregateRepository creditChecks;

  @Autowired
  private blueprint.workflowmodule.loanapproval.Service loanApproval;

  @Autowired
  private blueprint.workflowmodule.loanapproval.model.AggregateRepository loanApprovals;

  @Autowired
  private blueprint.workflowmodule.loanapproval.overdraft.Service overdraftRequest;

  @Autowired
  private blueprint.workflowmodule.loanapproval.overdraft.model.AggregateRepository overdrafts;

  /**
   * The agency answers, retrying while a remote BPMS does not know the credit check yet.
   *
   * @param creditCheckId The id of the credit check.
   * @param score         The score the agency gives.
   */
  private void agencyAnswers(
      final String creditCheckId,
      final int score) {

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
  @DisplayName("Each process which uses the credit check gets its own result back")
  public void eachProcessGetsItsOwnResult() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var overdraftId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);
    overdraftRequest.request(overdraftId, 1000);

    final var loanCreditCheckId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditCheckId() != null)
        .getCreditCheckId();
    final var overdraftCreditCheckId = awaitAggregate(
        overdrafts,
        overdraftId,
        aggregate -> aggregate.getCreditCheckId() != null)
        .getCreditCheckId();

    // two workflows of the same process, one per caller
    assertThat(loanCreditCheckId).isNotEqualTo(overdraftCreditCheckId);
    awaitAggregate(creditChecks, loanCreditCheckId, aggregate -> Boolean.TRUE.equals(aggregate.getAgencyAsked()));
    awaitAggregate(creditChecks, overdraftCreditCheckId, aggregate -> Boolean.TRUE.equals(aggregate.getAgencyAsked()));

    // answered in the other order than requested, with different scores
    agencyAnswers(overdraftCreditCheckId, 40);
    agencyAnswers(loanCreditCheckId, 90);

    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getApproved() != null);
    final var overdraft = awaitAggregate(
        overdrafts,
        overdraftId,
        aggregate -> aggregate.getGrantedLimit() != null);

    assertThat(loanRequest.getCreditScore()).isEqualTo(90);
    assertThat(loanRequest.getApproved()).isTrue();
    assertThat(overdraft.getCreditScore()).isEqualTo(40);
    assertThat(overdraft.getGrantedLimit()).isEqualTo(400);

  }

  @Test
  @DisplayName("An answer of the agency which arrives twice changes nothing")
  public void aRepeatedAnswerIsRefused() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000);

    final var creditCheckId = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditCheckId() != null)
        .getCreditCheckId();
    awaitAggregate(creditChecks, creditCheckId, aggregate -> Boolean.TRUE.equals(aggregate.getAgencyAsked()));

    agencyAnswers(creditCheckId, 70);
    awaitAggregate(creditChecks, creditCheckId, aggregate -> Boolean.TRUE.equals(aggregate.getReported()));

    // the application decides that, and keeps the second answer away from the BPMS
    creditCheck.agencyAnswered(creditCheckId, 10);

    assertThat(creditChecks.findById(creditCheckId).orElseThrow().getScore()).isEqualTo(70);
    final var loanRequest = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getApproved() != null);
    assertThat(loanRequest.getCreditScore()).isEqualTo(70);

  }

}
