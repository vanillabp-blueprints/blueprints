package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import blueprint.workflowmodule.loanapproval.Service;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * What this blueprint is about: two BPMS configured at once, and every operation reaching
 * the one holding its workflow.
 *
 * <p>
 * The tests run only where two adapters are configured, which is the {@code camunda8}
 * profile: it brings both adapters along, because a migration is the state in which the old
 * BPMS is still there. Under the other profile there is one BPMS and nothing to route, so
 * these tests are skipped rather than duplicated.
 * </p>
 *
 * <p>
 * Which BPMS holds a workflow is read the only way an application can read it: the id of a
 * process definition is {@code <adapter-id>#<id of the BPMS>}, and asking for it elects the
 * adapter like any other operation. That the assertion works at all is the proof.
 * </p>
 */
public class MigrationIT extends WorkflowModuleTest {

  private static final String NEW_BPMS = "camunda8";

  private static final String OLD_BPMS = "camunda7";

  @Autowired
  private Environment environment;

  @Autowired
  private Service loanApprovals;

  @Autowired
  private blueprint.workflowmodule.loanrepayment.RepaymentService repayments;

  @Autowired
  private AggregateRepository loanApprovalAggregates;

  // both packages of this module carry a class of that name, so one of the two is spelled out
  @Autowired
  private blueprint.workflowmodule.loanrepayment.model.RepaymentRepository repaymentAggregates;

  @Test
  @DisplayName("A new workflow starts in the first adapter of the priority list")
  public void aNewWorkflowStartsInTheFirstAdapter() {

    assumeTwoAdaptersAreConfigured();

    final var loanRequestId = UUID.randomUUID().toString();
    loanApprovals.initiateLoanApproval(loanRequestId, 5000);

    awaitAggregate(
        loanApprovalAggregates,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null);

    assertThat(loanApprovals.bpmsHolding(loanRequestId))
        .describedAs("the loan approval follows the adapter list, so it starts in the new BPMS")
        .contains(NEW_BPMS);

  }

  @Test
  @DisplayName("A workflow with a priority list of its own stays in the old BPMS")
  public void aPinnedWorkflowStartsWhereItsOwnListSays() {

    assumeTwoAdaptersAreConfigured();

    final var repaymentId = UUID.randomUUID().toString();
    repayments.initiateRepayment(repaymentId, 500);

    // the workflow runs to its end in the BPMS its own list names
    awaitAggregate(
        repaymentAggregates,
        repaymentId,
        aggregate -> Boolean.TRUE.equals(aggregate.getInstalmentBooked()));

    assertThat(repayments.bpmsHolding(repaymentId))
        .describedAs("'vanillabp...workflows.loan_repayment.prioritized-adapters' names the old BPMS")
        .contains(OLD_BPMS);

  }

  @Test
  @DisplayName("Both wait states of a workflow are answered by the BPMS holding it")
  public void everyOperationReachesTheBpmsHoldingTheWorkflow() {

    assumeTwoAdaptersAreConfigured();

    final var loanRequestId = UUID.randomUUID().toString();
    loanApprovals.initiateLoanApproval(loanRequestId, 5000);

    final var waiting = awaitAggregate(
        loanApprovalAggregates,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() != null);

    // completing the user task and correlating the message both elect the adapter; an
    // election going wrong ends in an exception instead of in a finished workflow
    loanApprovals.assessRisk(loanRequestId, waiting.getRiskAssessmentTaskId());
    awaitAggregate(
        loanApprovalAggregates,
        loanRequestId,
        aggregate -> aggregate.getRiskAssessmentTaskId() == null);

    loanApprovals.contractSigned(loanRequestId, "Jane Doe");
    final var paidOut = awaitAggregate(
        loanApprovalAggregates,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getPaidOut()));

    assertThat(paidOut.getContractSignedBy()).isEqualTo("Jane Doe");
    assertThat(loanApprovals.bpmsHolding(loanRequestId))
        .describedAs("the workflow ran to its end in the BPMS it was started in")
        .contains(NEW_BPMS);

  }

  /**
   * The subject of these tests is what happens with two BPMS, so they run where two are
   * configured. That is the state the 'camunda8' profile brings, and asking the
   * configuration for the priority list says so more directly than asking for the profile.
   */
  private void assumeTwoAdaptersAreConfigured() {

    assumeTrue(
        environment.getProperty("vanillabp.prioritized-adapters[0]") != null,
        "these tests need two adapters, which is what 'vanillabp.prioritized-adapters' says");

  }

}
