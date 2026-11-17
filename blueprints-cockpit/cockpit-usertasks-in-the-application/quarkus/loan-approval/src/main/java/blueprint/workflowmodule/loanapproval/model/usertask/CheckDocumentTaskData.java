package blueprint.workflowmodule.loanapproval.model.usertask;

/**
 * What the form of the user task "Check document" holds. Several of these tasks are open at
 * the same time, one per document. So the data names the document, which is set when the task
 * opens and never changes.
 *
 * @param documentId Which document this task is about.
 * @param valid      Whether the document is in order, empty while nobody has decided.
 * @param remark     What the user noted about the document.
 */
public record CheckDocumentTaskData(
                                    String documentId,
                                    Boolean valid,
                                    String remark) implements TaskData {

  /**
   * The form as it is when the task opens.
   *
   * @param documentId Which document the task is about.
   * @return A form which names the document and has no values.
   */
  public static CheckDocumentTaskData of(
      final String documentId) {

    return new CheckDocumentTaskData(documentId, null, null);

  }

  /**
   * The same document with new values, which is what a save or a completion stores.
   *
   * @param valid  Whether the document is in order.
   * @param remark What the user noted.
   * @return The new values for the same document.
   */
  public CheckDocumentTaskData with(
      final Boolean valid,
      final String remark) {

    return new CheckDocumentTaskData(documentId, valid, remark);

  }

}
