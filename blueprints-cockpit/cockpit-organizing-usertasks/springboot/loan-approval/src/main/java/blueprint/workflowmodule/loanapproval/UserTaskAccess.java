package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * Who may read the form of a user task without the authority for it: everybody who worked on
 * the task before. The endpoints which read a form name this bean in their
 * {@code @PreAuthorize}, next to the authority of the task.
 *
 * <p>
 * The Business Cockpit shows the task to the same people, because the details provider reports
 * them as the admitted users of the task. Both read the same list, the editors of the task.
 * That is the point: if the cockpit showed a task and the form then answered 403, the admitted
 * users of the cockpit would be worth nothing.
 * </p>
 *
 * <p>
 * It is an ordinary bean, and it reads only the ids of the editors, not the whole case. The
 * check runs on every request to a form.
 * </p>
 */
@Component
public class UserTaskAccess {

  @Autowired
  private AggregateRepository loanApprovals;

  /**
   * Whether the user worked on the user task before and may read it.
   *
   * @param loanRequestId The id of the loan request.
   * @param taskId        The id of the user task.
   * @param user          The user who asks.
   * @return True if the user opened the form to work on it, or completed it.
   */
  @Transactional(readOnly = true)
  public boolean mayRead(
      final String loanRequestId,
      final String taskId,
      final String user) {

    return loanApprovals
        .findEditors(loanRequestId, taskId)
        .contains(user);

  }

}
