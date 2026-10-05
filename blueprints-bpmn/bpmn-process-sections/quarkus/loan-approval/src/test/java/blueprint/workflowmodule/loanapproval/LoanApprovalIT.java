package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
 * and waits for the process to have done its work.
 *
 * <p>
 * The aspect under test is what a section leaves behind. A small loan skips the second
 * section, so its aggregate has to reach a decision with that section's sub-object still
 * {@code null}. A test which only ran the big loans would be green while every small loan
 * was stuck.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  private Aggregate runWith(
      final int amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    return awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getDecision() != null);

  }

  @Test
  @DisplayName("A loan below the limit is decided without the second section")
  public void aSkippedSectionLeavesItsSubObjectNull() {

    // below the configured limit of 10000, so the risk assessment never runs
    final var loanRequest = runWith(5000);

    // the first section ran and left its sub-object behind
    assertThat(loanRequest.getDocumentCheck()).isNotNull();
    assertThat(loanRequest.getDocumentCheck().getSignaturesComplete()).isTrue();

    // the second section did not, and this is the state every getter has to survive
    assertThat(loanRequest.getRiskAssessment()).isNull();
    assertThat(loanRequest.isRiskAssessed()).isFalse();
    assertThat(loanRequest.isCollateralSufficient()).isFalse();

    assertThat(loanRequest.getDecision()).isEqualTo("approved");

  }

  @Test
  @DisplayName("A loan above the limit runs the second section and is decided on its result")
  public void aSectionWhichRunsFillsItsSubObject() {

    // above the limit, and the security covers enough of it
    final var loanRequest = runWith(12000);

    // the first section read its own finding inside its own model: one document is missing
    assertThat(loanRequest.isSignaturesComplete()).isFalse();

    assertThat(loanRequest.isRiskAssessed()).isTrue();
    assertThat(loanRequest.getRiskAssessment().getCollateralValue()).isEqualTo(7200);
    assertThat(loanRequest.getRiskAssessment().getDebtRatio()).isEqualTo(24);
    assertThat(loanRequest.isCollateralSufficient()).isTrue();

    assertThat(loanRequest.getDecision()).isEqualTo("approved");

  }

  @Test
  @DisplayName("What the second section found decides the loan")
  public void whatTheSectionFoundDecidesTheLoan() {

    // the security is capped at 12000, which does not cover half of this loan
    final var loanRequest = runWith(30000);

    assertThat(loanRequest.getRiskAssessment().getCollateralValue()).isEqualTo(12000);
    assertThat(loanRequest.isCollateralSufficient()).isFalse();

    assertThat(loanRequest.getDecision()).isEqualTo("rejected");

  }

  @Test
  @DisplayName("The getters the model reads answer on an aggregate without any section")
  public void theSharedGettersSurviveEverySectionBeingAbsent() {

    // the state of a workflow which has not reached a single section yet
    final var loanRequest = new Aggregate();

    assertThatCode(() -> {
      assertThat(loanRequest.isRiskAssessmentRequired()).isFalse();
      assertThat(loanRequest.isRiskAssessed()).isFalse();
      assertThat(loanRequest.isSignaturesComplete()).isFalse();
      assertThat(loanRequest.isCollateralSufficient()).isFalse();
    }).doesNotThrowAnyException();

  }

}
