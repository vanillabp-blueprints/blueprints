package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have done its work - across the call activity, which is the
 * aspect of this blueprint.
 *
 * <p>
 * The test knows one process. It starts the loan approval and waits for the decision, and
 * the risk assessment shows up in it only as the two attributes the called process wrote.
 * That is what a decomposition is supposed to look like from the outside, and it is why a
 * test written against the aggregate survives the model being split up differently
 * tomorrow.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service service;

  @Inject
  AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, amount);

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getDecision() != null);

  }

  @Test
  @DisplayName("The called process works on the aggregate of the calling one")
  public void theCallActivityRunsTheRiskAssessmentOnTheSameAggregate() {

    // 5000 / 100 is a rating of 50, the configured minimum is 30
    final var loanApproval = runWith(5000);

    // written by the calling process, before the call activity
    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    // written by the called process - on the same aggregate, which is the point
    assertThat(loanApproval.getCollateralValue()).isEqualTo(3000);
    assertThat(loanApproval.getDebtRatio()).isEqualTo(10);
    // written by the calling process, after the call activity returned
    assertThat(loanApproval.getDecision()).isEqualTo("approved");

  }

  @Test
  @DisplayName("What the called process wrote decides the loan")
  public void theResultOfTheCalledProcessIsUsedAfterTheCallActivity() {

    // a debt ratio of 60% - above the configured maximum of 40, while the rating is fine
    final var loanApproval = runWith(30000);

    assertThat(loanApproval.getCreditRating()).isEqualTo(100);
    assertThat(loanApproval.getDebtRatio()).isEqualTo(60);
    assertThat(loanApproval.getDecision()).isEqualTo("rejected");

  }

}
