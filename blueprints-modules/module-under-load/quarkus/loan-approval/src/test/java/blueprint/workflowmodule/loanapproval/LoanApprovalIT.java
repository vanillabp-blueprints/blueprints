package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The integration test of this workflow module: it walks one loan approval along each path of
 * the process, in a real BPMS.
 *
 * <p>
 * Load is not tested here. The load driver runs in the simulator, and the test of the
 * application runs it. What this test proves is the part the driver relies on: every shape of
 * the process works on its own. The credit bureau answers with a message, or the process stops
 * waiting for it. A clerk decides. An approved loan waits for the payout run and is paid out
 * when it comes.
 * </p>
 */
@QuarkusTest
public class LoanApprovalIT extends WorkflowModuleTest {

  @Inject
  Service loanApproval;

  @Inject
  AggregateRepository loanApprovals;

  @Inject
  ScriptedCreditBureau creditBureau;

  @Test
  public void anApprovedLoanIsPaidOutInTheNextPayoutRun() {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, 5000, "documents");

    final var assessment = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> aggregate.getAssessmentTaskId() != null);
    assertThat(assessment.getCreditScore())
        .describedAs("The answer of the credit bureau reached the workflow before the clerk")
        .isEqualTo(ScriptedCreditBureau.SCORE);

    loanApproval.assess(loanRequestId, assessment.getAssessmentTaskId(), true);

    // The payout run is a timer of the model, so this step waits for the scheduler of the BPMS.
    final var paidOut = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        SCHEDULER_TIMEOUT,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));
    assertThat(paidOut.getPayoutRunAt()).isNotNull();
    assertThat(paidOut.getAssessmentTaskId()).isNull();

  }

  @Test
  public void aClerkAssessesTheLoanWithoutAScoreWhenTheBureauStaysSilent() {

    final var loanRequestId = UUID.randomUUID().toString();
    creditBureau.staySilentAbout(loanRequestId);

    loanApproval.request(loanRequestId, 5000, "documents");

    // The process gives the bureau ten seconds, with a timer. So this step waits for the
    // scheduler of the BPMS.
    final var assessment = awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        SCHEDULER_TIMEOUT,
        aggregate -> aggregate.getAssessmentTaskId() != null);
    assertThat(assessment.getCreditScore()).isNull();

    loanApproval.assess(loanRequestId, assessment.getAssessmentTaskId(), false);

    awaitAggregate(
        loanApprovals::findByIdOptional,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getRejectionSent()));

  }

}
