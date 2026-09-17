package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.audit.ChangeAuthor;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module. It plays the case through and asks the
 * two questions the auditing exists for: what did the archive hear, and who changed what.
 *
 * <p>
 * The decision and the payout are two transactions, and the notice about the decision is
 * sent between them - late, because the archive is away at the first attempt. So by the
 * time the notice is delivered the loan approval has moved on, which is the situation
 * this blueprint is about and the reason the test can assert on the difference.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /** The surrounding system, replaced by a simulator the test can read. */
  @TestConfiguration
  static class Simulators {

    @Bean
    @Primary
    ComplianceArchiveSimulator complianceArchive() {

      return new ComplianceArchiveSimulator();

    }

  }

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private ComplianceArchiveSimulator complianceArchive;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    complianceArchive.reset();

  }

  /**
   * Starts a loan approval and answers its risk assessment, the way the API does it: the
   * name of the person acting is bound around the call, because the change is written
   * when that call commits.
   *
   * @return The id of the loan approval, decided and on its way to the payout.
   */
  private String decidedLoanApproval() {

    final var loanRequestId = UUID.randomUUID().toString();

    ChangeAuthor.attributeTo(
        "the customer",
        () -> service.initiateLoanApproval(loanRequestId, 5000, "the customer"));

    final var waitingForTheDecision = awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanApproval -> loanApproval.getAssessRiskTaskId() != null);

    ChangeAuthor.attributeTo(
        "paula",
        () -> service.assessRisk(
            loanRequestId,
            waitingForTheDecision.getAssessRiskTaskId(),
            true,
            "paula"));

    return loanRequestId;

  }

  @Test
  @DisplayName("The notice reports the loan approval as it was when the decision was taken")
  public void theNoticeReportsTheStateOfTheDecision() {

    final var loanRequestId = decidedLoanApproval();

    final var today = awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanApproval -> loanApproval.getPaidOut() != null);
    assertThat(today.getPaidOut())
        .describedAs("the process moved on after the decision")
        .isTrue();

    final var asItWas = complianceArchive.awaitNotice(loanRequestId);

    assertThat(asItWas.getRiskAcceptable())
        .describedAs("the decision the notice is about")
        .isTrue();
    assertThat(asItWas.getDecidedBy()).isEqualTo("paula");
    assertThat(asItWas.getPaidOut())
        .describedAs("the payout was not booked yet when the decision was taken")
        .isNull();

    assertThat(complianceArchive.invocations())
        .describedAs("the archive was away at the first attempt, so the notice waited")
        .filteredOn(invocation -> invocation.equals("asked about "
            + loanRequestId))
        .hasSize(2);

  }

  @Test
  @DisplayName("The trail names every state of the loan approval and who caused it")
  public void theTrailNamesEveryChangeAndItsAuthor() {

    final var loanRequestId = decidedLoanApproval();

    awaitAggregate(
        loanApprovals,
        loanRequestId,
        loanApproval -> loanApproval.getPaidOut() != null);

    final var trail = service.getTrail(loanRequestId);

    assertThat(trail)
        .describedAs("the request, the credit rating, the open task, the decision, the payout")
        .hasSize(5);
    assertThat(trail.get(0)).contains("by the customer");
    assertThat(trail.get(1)).contains("by "
        + ChangeAuthor.THE_PROCESS);
    // keeping the id of the open user task is a change of the loan approval like any
    // other, which is what makes a trail longer than the list of business steps
    assertThat(trail.get(2)).contains("by "
        + ChangeAuthor.THE_PROCESS);
    assertThat(trail.get(3)).contains("by paula");
    assertThat(trail.get(4)).contains("by "
        + ChangeAuthor.THE_PROCESS);

  }

}
