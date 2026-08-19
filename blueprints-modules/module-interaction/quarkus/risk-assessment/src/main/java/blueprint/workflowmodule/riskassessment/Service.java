package blueprint.workflowmodule.riskassessment;

import java.util.Optional;

import blueprint.api.RiskAssessed;
import blueprint.api.RiskAssessments;
import blueprint.workflowmodule.riskassessment.config.RiskAssessmentProperties;
import blueprint.workflowmodule.riskassessment.model.Aggregate;
import blueprint.workflowmodule.riskassessment.model.AggregateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of the risk assessment, and at the same time the implementation of
 * what this module offers the rest of the application ({@link RiskAssessments}).
 *
 * <p>
 * That the offer is answered by a process is this module's own business. A caller sees a
 * method which accepts a request and an event which arrives later; whether a BPMN process, a
 * lookup table or a rating agency produced it is not part of the contract and may change.
 * </p>
 *
 * <p>
 * Nothing here knows the loan approval. The answer is published, not delivered: a module
 * which knew its callers could not be deployed without them.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class Service implements RiskAssessments {

  @Inject
  AggregateRepository assessments;

  @Inject
  Workflow workflow;

  @Inject
  RiskAssessmentProperties properties;

  @Inject
  Event<RiskAssessed> events;

  @Override
  @Transactional
  public void requestAssessment(
      final String caseId,
      final int amount) {

    final var assessment = Aggregate
        .builder()
        .caseId(caseId)
        .amount(amount)
        .build();

    workflow.assessmentRequested(assessment);

    log.info("Risk assessment of case '{}' started", caseId);

  }

  /**
   * Works out the risk. A real application would ask a scoring service here; what matters for
   * the blueprint is that this is ordinary business code of this module, reached through its
   * own process.
   *
   * @param assessment The assessment to work out.
   */
  public void evaluateRisk(
      final Aggregate assessment) {

    final var score = Math.min(
        properties.scoreLimit(),
        assessment.getAmount() / 200);

    assessment.setScore(score);

    log.info(
        "Risk score of case '{}' is {}",
        assessment.getCaseId(),
        score);

  }

  /**
   * Announces the result. Whoever asked hears it as an event; this module does not know who
   * that is, and it does not wait for anybody either.
   *
   * @param assessment The assessment which is done.
   */
  public void publishAssessment(
      final Aggregate assessment) {

    events.fire(new RiskAssessed(assessment.getCaseId(), assessment.getScore()));

    log.info("Risk assessment of case '{}' published", assessment.getCaseId());

  }

  /**
   * The state of an assessment, as far as the process has come.
   *
   * @param caseId The id of the case.
   * @return The assessment, if it exists.
   */
  @Transactional
  public Optional<Aggregate> getAssessment(
      final String caseId) {

    return assessments.findByIdOptional(caseId);

  }

}
