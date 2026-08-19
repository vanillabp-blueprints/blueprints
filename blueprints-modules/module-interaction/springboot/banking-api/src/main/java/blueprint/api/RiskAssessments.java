package blueprint.api;

/**
 * What the risk assessment offers to the rest of the application: ask for an assessment of a
 * case, get an answer later.
 *
 * <p>
 * This interface is the whole contract between two workflow modules. It carries no BPMN, no
 * aggregate and no {@code ProcessService} - only what one use case wants from the other, in
 * the words of the business. Whether the answer takes a process, a table lookup or a call to
 * a rating agency is the answering module's business, and it may change without anybody
 * noticing.
 * </p>
 *
 * <p>
 * It lives in a JAR of its own so that neither workflow module depends on the other. The
 * asking module compiles against this interface, the answering module implements it, and the
 * application puts them together.
 * </p>
 */
public interface RiskAssessments {

  /**
   * Asks for a case to be assessed. The call returns as soon as the request is accepted; the
   * answer arrives later as a {@link RiskAssessed} event.
   *
   * @param caseId The id of the business case, chosen by the caller and echoed in the answer.
   * @param amount The amount at risk.
   */
  void requestAssessment(
      String caseId,
      int amount);

}
