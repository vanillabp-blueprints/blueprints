package blueprint.workflowmodule.loanapproval.support;

/**
 * The user task exists, but it was completed or canceled already. A form sent twice, or a
 * form sent after the workflow took the task away.
 *
 * <p>
 * Every application with forms of its own needs this, so it is in the package
 * {@code support} and names no use case. {@link UserTaskExceptionHandler} answers it with
 * 409.
 * </p>
 */
public class TaskNotOpenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * The task is done.
   *
   * @param workflowId The id of the workflow aggregate the form named.
   * @param taskId     The id of the user task the form named.
   */
  public TaskNotOpenException(
      final String workflowId,
      final String taskId) {

    super("The user task '"
        + taskId
        + "' of '"
        + workflowId
        + "' is not open any more");

  }

}
