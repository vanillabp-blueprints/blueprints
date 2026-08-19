package blueprint.api;

/**
 * The answer to a {@link RiskAssessments#requestAssessment(String, int)}, published as an
 * event rather than returned as a value.
 *
 * <p>
 * An event is what keeps the direction of the dependency straight. The asking module knows
 * whom it asks; the answering module must not know who asked, or the two modules would
 * depend on each other and the second use case could no longer be deployed without the
 * first. So the answer is announced, and whoever waits for it listens.
 * </p>
 *
 * @param caseId The id the assessment was requested for.
 * @param score  What came out of it, higher meaning riskier.
 */
public record RiskAssessed(String caseId, int score) {
}
