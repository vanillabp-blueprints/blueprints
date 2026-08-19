package blueprint.workflowmodule.riskassessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import blueprint.api.RiskAssessed;
import blueprint.api.RiskAssessments;
import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.riskassessment.model.AggregateRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * The integration test of the answering workflow module, and it runs without the module which
 * asks: that is the point of the interface. The test calls
 * {@link RiskAssessments#requestAssessment(String, int)} exactly as the other module does,
 * and waits for the event this module publishes.
 */
@QuarkusTest
public class RiskAssessmentIT extends WorkflowModuleTest {

  /**
   * Stands in for whoever listens in the running application. A test needs the same thing a
   * caller needs, and it is three lines - which says something about how loosely the two
   * modules are coupled.
   */
  @ApplicationScoped
  public static class Answers {

    private final CopyOnWriteArrayList<RiskAssessed> received = new CopyOnWriteArrayList<>();

    void onRiskAssessed(
        @Observes final RiskAssessed event) {

      received.add(event);

    }

    /**
     * Asked through a method rather than by reading the list: what a test holds of a bean is
     * the container's client proxy, and a proxy forwards calls, not field access.
     *
     * @return What arrived so far.
     */
    public List<RiskAssessed> received() {

      return List.copyOf(received);

    }

  }

  @Inject
  RiskAssessments riskAssessments;

  @Inject
  AggregateRepository assessments;

  @Inject
  Answers answers;

  @Test
  public void anAssessmentAnswersWithAnEvent() {

    final var caseId = UUID.randomUUID().toString();

    riskAssessments.requestAssessment(caseId, 6000);

    final var assessment = awaitAggregate(
        assessments::findByIdOptional,
        caseId,
        aggregate -> aggregate.getScore() != null);

    assertThat(assessment.getScore()).isEqualTo(30);

    await()
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(200))
        .until(() -> answers
            .received()
            .stream()
            .anyMatch(event -> event.caseId().equals(caseId)));

    assertThat(answers.received())
        .describedAs("the answer carries what the caller asked about, and nothing of this module")
        .anySatisfy(event -> {
          assertThat(event.caseId()).isEqualTo(caseId);
          assertThat(event.score()).isEqualTo(30);
        });

  }

}
