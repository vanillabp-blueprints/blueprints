package blueprint.workflowmodule.riskassessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import blueprint.api.RiskAssessed;
import blueprint.api.RiskAssessments;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.riskassessment.model.AggregateRepository;

/**
 * The integration test of the answering workflow module, and it runs without the module which
 * asks: that is the point of the interface. The test calls
 * {@link RiskAssessments#requestAssessment(String, int)} exactly as the other module does,
 * and waits for the event this module publishes.
 */
public class RiskAssessmentIT extends WorkflowModuleTest {

  /**
   * Stands in for whoever listens in the running application. A test needs the same thing a
   * caller needs, and it is three lines - which says something about how loosely the two
   * modules are coupled.
   */
  @Component
  static class Answers {

    private final CopyOnWriteArrayList<RiskAssessed> received = new CopyOnWriteArrayList<>();

    @EventListener
    void onRiskAssessed(
        final RiskAssessed event) {

      received.add(event);

    }

  }

  @Autowired
  private RiskAssessments riskAssessments;

  @Autowired
  private AggregateRepository assessments;

  @Autowired
  private Answers answers;

  @Test
  public void anAssessmentAnswersWithAnEvent() {

    final var caseId = UUID.randomUUID().toString();

    riskAssessments.requestAssessment(caseId, 6000);

    final var assessment = awaitAggregate(
        assessments,
        caseId,
        aggregate -> aggregate.getScore() != null);

    assertThat(assessment.getScore()).isEqualTo(30);

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> answers.received
            .stream()
            .anyMatch(event -> event.caseId().equals(caseId)));

    assertThat(answers.received)
        .describedAs("the answer carries what the caller asked about, and nothing of this module")
        .anySatisfy(event -> {
          assertThat(event.caseId()).isEqualTo(caseId);
          assertThat(event.score()).isEqualTo(30);
        });

  }

}
