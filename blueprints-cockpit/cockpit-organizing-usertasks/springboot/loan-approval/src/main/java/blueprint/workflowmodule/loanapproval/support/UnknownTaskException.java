package blueprint.workflowmodule.loanapproval.support;

/**
 * There is no user task of that id and kind in that workflow. A form sent a task id which does
 * not belong to the workflow, or which belongs to a task of another kind.
 *
 * <p>
 * Every application with forms of its own needs this, so it is in the package
 * {@code support} and names no use case. {@link UserTaskExceptionHandler} answers it with
 * 404.
 * </p>
 */
public class UnknownTaskException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * The task is not there.
   *
   * @param workflowId The id of the workflow aggregate the form named.
   * @param taskId     The id of the user task the form named.
   */
  public UnknownTaskException(
      final String workflowId,
      final String taskId) {

    super("There is no such user task '"
        + taskId
        + "' in '"
        + workflowId
        + "'");

  }

}
