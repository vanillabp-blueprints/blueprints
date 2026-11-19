package blueprint.workflowmodule.loanapproval.overdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.overdraft.model.AggregateRepository;
import io.vanillabp.spi.process.WorkflowNotFoundException;

/**
 * The test of the second process which uses the credit check. Same credit check, another
 * context, another use of the score.
 */
public class OverdraftRequestIT extends WorkflowModuleTest {

  @Autowired
  private Service overdraftRequest;

  @Autowired
  private AggregateRepository overdrafts;

  @Autowired
  private blueprint.workflowmodule.loanapproval.creditcheck.Service creditCheck;

  @Autowired
  private blueprint.workflowmodule.loanapproval.creditcheck.model.AggregateRepository creditChecks;

  @Test
  @DisplayName("The overdraft request gets its result from the same credit check process")
  public void theOverdraftRequestUsesTheSameCreditCheck() {

    final var overdraftId = UUID.randomUUID().toString();

    overdraftRequest.request(overdraftId, 1000);

    final var creditCheckId = awaitAggregate(
        overdrafts,
        overdraftId,
        aggregate -> aggregate.getCreditCheckId() != null)
        .getCreditCheckId();

    final var check = awaitAggregate(
        creditChecks,
        creditCheckId,
        aggregate -> Boolean.TRUE.equals(aggregate.getAgencyAsked()));
    assertThat(check.getRequestedBy()).isEqualTo(Service.CREDIT_CHECK_REQUESTER);
    assertThat(check.getCaseId()).isEqualTo(overdraftId);

    // the agency answers, retrying while a remote BPMS does not know the credit check yet
    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(500))
        .ignoreException(WorkflowNotFoundException.class)
        .until(() -> {
          creditCheck.agencyAnswered(creditCheckId, 70);
          return true;
        });

    final var overdraft = awaitAggregate(
        overdrafts,
        overdraftId,
        aggregate -> aggregate.getGrantedLimit() != null);

    assertThat(overdraft.getCreditScore()).isEqualTo(70);
    assertThat(overdraft.getGrantedLimit()).isEqualTo(700);

  }

}
