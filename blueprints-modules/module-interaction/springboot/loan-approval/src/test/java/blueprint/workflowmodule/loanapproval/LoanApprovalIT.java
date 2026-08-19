package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import blueprint.api.RiskAssessed;
import blueprint.api.RiskAssessments;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module, and it runs without the module which answers.
 *
 * <p>
 * That is what the interface buys: the other module is stood in for by the few lines below,
 * so this test proves what this module does - ask, wait, take the answer - without a second
 * process, a second database table or a second BPMN model being involved. If standing the
 * other module in took more than this, the two would be coupled more tightly than they look.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /**
   * The other workflow module, as far as this one is concerned: it accepts requests and
   * answers when the test says so. Answering later rather than inside the call is what the
   * real module does as well, because its own process runs in transactions of its own.
   */
  @Component
  static class RiskAssessmentsStub implements RiskAssessments {

    private final CopyOnWriteArrayList<String> requested = new CopyOnWriteArrayList<>();

    @Autowired
    private ApplicationEventPublisher events;

    @Override
    public void requestAssessment(
        final String caseId,
        final int amount) {

      requested.add(caseId);

    }

    void answer(
        final String caseId,
        final int score) {

      events.publishEvent(new RiskAssessed(caseId, score));

    }

  }

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private RiskAssessmentsStub riskAssessments;

  @Test
  public void theProcessAsksTheOtherModuleAndWaitsForItsAnswer() {

    final var loanRequestId = UUID.randomUUID().toString();

    service.initiateLoanApproval(loanRequestId, 5000);

    final var rated = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getCreditRating() != null);

    assertThat(rated.getCreditRating()).isEqualTo(50);

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> riskAssessments.requested.contains(loanRequestId));

    assertThat(loanApprovals.findById(loanRequestId).orElseThrow().getRiskScore())
        .describedAs("the process waits for the answer instead of running on without it")
        .isNull();

    riskAssessments.answer(loanRequestId, 25);

    final var assessed = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRiskScore() != null);

    assertThat(assessed.getRiskScore()).isEqualTo(25);

  }

}
