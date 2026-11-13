package blueprint.workflowmodule.loanapproval.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The answers to the two exceptions of this package, for every controller. A form gets the
 * status and the message, and can show the message to the user.
 */
@RestControllerAdvice
public class UserTaskExceptionHandler {

  /**
   * A task id which does not belong to the workflow, or belongs to a task of another kind.
   *
   * @param e What the service found.
   * @return The message, for the form to show.
   */
  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String unknownTask(
      final UnknownTaskException e) {

    return e.getMessage();

  }

  /**
   * A task which was completed or canceled before, for example a form sent twice.
   *
   * @param e What the service found.
   * @return The message, for the form to show.
   */
  @ExceptionHandler
  @ResponseStatus(HttpStatus.CONFLICT)
  public String taskNotOpen(
      final TaskNotOpenException e) {

    return e.getMessage();

  }

}
