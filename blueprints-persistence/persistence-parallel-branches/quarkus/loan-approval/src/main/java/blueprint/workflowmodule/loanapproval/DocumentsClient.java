package blueprint.workflowmodule.loanapproval;

/**
 * The document service of the loan approval, as far as this application is concerned.
 *
 * <p>
 * A port rather than a class, so the test can put a simulator in its place. That simulator
 * is what makes the collision of the two branches reproducible: it holds the branch inside
 * its transaction until the test has answered the other one.
 * </p>
 */
public interface DocumentsClient {

  /**
   * Asks the document service for the documents of a loan request.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return How many documents were handed over.
   */
  int collect(
      String loanRequestId);

}
