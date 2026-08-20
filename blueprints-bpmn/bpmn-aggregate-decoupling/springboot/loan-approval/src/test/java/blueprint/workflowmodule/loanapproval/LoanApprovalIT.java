package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import blueprint.workflowmodule.loanapproval.model.RiskClass;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and waits for the process to have taken one of its three branches.
 *
 * <p>
 * One test per risk class, steered by the amount alone. What the tests assert is the
 * outcome the process wrote, never the attribute a coupled model would have read, and that
 * is the aspect of this blueprint: the process routes on questions, and the questions keep
 * working when the data behind them changes.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, "Alex Customer", amount);

    return awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getOutcome() != null);

  }

  @Test
  @DisplayName("A low risk is approved, and the model never learned what a low risk is")
  public void aLowRiskIsApproved() {

    // 5000 / 100 is a rating of 50, the configured low-risk rating is 30
    final var loanApproval = runWith(5000);

    assertThat(loanApproval.getCreditRating()).isEqualTo(50);
    assertThat(loanApproval.getRiskClass()).isEqualTo(RiskClass.LOW);
    assertThat(loanApproval.isApprovableWithoutReview()).isTrue();
    assertThat(loanApproval.getOutcome()).isEqualTo("approved");

  }

  @Test
  @DisplayName("A medium risk goes to a person")
  public void aMediumRiskGoesToAManualReview() {

    // a rating of 15: below the low-risk rating of 30, at or above the medium one of 10
    final var loanApproval = runWith(1500);

    assertThat(loanApproval.getRiskClass()).isEqualTo(RiskClass.MEDIUM);
    assertThat(loanApproval.isWorthAManualReview()).isTrue();
    assertThat(loanApproval.getOutcome()).isEqualTo("under-review");

  }

  @Test
  @DisplayName("A high risk takes the default flow, because neither question was answered with yes")
  public void aHighRiskIsRejected() {

    // a rating of 3, below both thresholds
    final var loanApproval = runWith(300);

    assertThat(loanApproval.getRiskClass()).isEqualTo(RiskClass.HIGH);
    assertThat(loanApproval.isApprovableWithoutReview()).isFalse();
    assertThat(loanApproval.isWorthAManualReview()).isFalse();
    assertThat(loanApproval.getOutcome()).isEqualTo("rejected");

  }

  @Test
  @DisplayName("The customer's name stays in the application")
  public void personalDataIsNotNeededByTheProcess() {

    final var loanApproval = runWith(5000);

    // no condition of the model reads it and the aggregate does not share it, so the
    // process ran to its end without the BPMS ever being told who is asking
    assertThat(loanApproval.getCustomerName()).isEqualTo("Alex Customer");
    assertThat(loanApproval.getOutcome()).isEqualTo("approved");

  }

}
