package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import blueprint.workflowmodule.loanapproval.config.CockpitClientAutoConfiguration;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialCurrentUserApi;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.Person;
import io.vanillabp.cockpit.gui.api.v1.client.model.User;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;

/**
 * Asks the Business Cockpit who took over a user task.
 *
 * <p>
 * The cockpit keeps the assignee of a user task. A user takes a task over in the cockpit, and
 * the cockpit writes it into its own store. It tells neither the BPMS nor the workflow module. So
 * the module asks the cockpit, at the moment a form completes the task.
 * </p>
 *
 * <p>
 * It asks through the official GUI API of the cockpit, with the clients generated from its
 * OpenAPI file. The clients send the cookie of the request along on their own. The cookie holds
 * the login of the user who sent the form, so the cockpit answers as that user. It answers two
 * questions: who the user is ({@code GET /app/current-user}), and whom the task is assigned to
 * ({@code GET /usertask/{id}}). The module needs no key and no user directory for this, because
 * the cockpit checks the login.
 * </p>
 *
 * <p>
 * The keys of the clients are below {@value CockpitClientAutoConfiguration#KEYS}, for example
 * {@code loan-approval.cockpit.base-url}. A request without a cookie asks nothing, so the URLs in
 * the log still work in a browser alone. Every answer which is not clear is read the same way:
 * nobody else took the task. A form which is refused for no reason is worse than a form which is
 * not refused.
 * </p>
 */
@Component
public class Cockpit {

  /**
   * The task list of the cockpit, with the keys of this module. It is a type of its own, so a
   * second module in the same application asks the cockpit with keys of its own.
   */
  public interface Tasklist extends OfficialTasklistApi {
  }

  /** Who is logged in, with the keys of this module. */
  public interface CurrentUser extends OfficialCurrentUserApi {
  }

  private final Tasklist tasklist;

  private final CurrentUser currentUser;

  /**
   * Takes the clients of the official GUI API.
   *
   * @param tasklist    Whom a user task is assigned to.
   * @param currentUser Who sent the request.
   */
  public Cockpit(
      final Tasklist tasklist,
      final CurrentUser currentUser) {

    this.tasklist = tasklist;
    this.currentUser = currentUser;

  }

  /**
   * Whether the cockpit says that the user task is assigned to another user than the one who
   * sent the request.
   *
   * @param userTaskId The id of the user task.
   * @param cookie     The {@code Cookie} header of the request, or {@code null}.
   * @return True only if the task has an assignee and it is somebody else.
   */
  public boolean saysSomebodyElseTookOver(
      final String userTaskId,
      final String cookie) {

    if (cookie == null) {
      return false;
    }

    try {

      final var assignee = Optional
          .ofNullable(tasklist.getUserTask(userTaskId, null).getBody())
          .map(UserTask::getAssignee)
          .map(Person::getId);
      if (assignee.isEmpty()) {
        return false;
      }

      final var user = Optional
          .ofNullable(currentUser.currentUser(null).getBody())
          .map(User::getId);
      return user.isPresent() && !assignee.get().equals(user.get());

    } catch (RestClientException e) {
      // An error status, or no answer at all. Nothing says that somebody else took the task.
      return false;
    }

  }

}
