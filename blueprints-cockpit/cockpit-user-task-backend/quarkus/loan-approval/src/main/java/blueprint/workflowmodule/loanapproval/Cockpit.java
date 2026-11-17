package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.Startup;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialCurrentUserApi;
import io.vanillabp.cockpit.gui.api.v1.client.OfficialTasklistApi;
import io.vanillabp.cockpit.gui.api.v1.client.model.Person;
import io.vanillabp.cockpit.gui.api.v1.client.model.User;
import io.vanillabp.cockpit.gui.api.v1.client.model.UserTask;
import io.vanillabp.cockpit.support.rest.restclient.RestClients;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

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
 * The support library of the cockpit builds the clients from the keys below {@value #KEYS}:
 * {@code base-url}, the timeouts, a proxy, retries and the rest. The keys belong to this module
 * and not to {@code vanillabp}, so a second module in the same application asks the cockpit
 * with values of its own. The clients are built while the application starts, so a wrong key
 * stops the start with a message.
 * </p>
 *
 * <p>
 * The module asks only if the application sets {@code loan-approval.cockpit.base-url}. Without
 * it, and without a cookie, a form completes the task as before. So the URLs in the log still
 * work in a browser alone, and so does the dev shell, which does not know who is logged in.
 * Every answer which is not clear is read the same way: nobody else took the task. A form which
 * is refused for no reason is worse than a form which is not refused.
 * </p>
 */
@ApplicationScoped
@Startup
public class Cockpit {

  /** Where the keys of the clients are, for example {@code loan-approval.cockpit.base-url}. */
  public static final String KEYS = "loan-approval.cockpit";

  private final OfficialTasklistApi tasklist;

  private final OfficialCurrentUserApi currentUser;

  /**
   * Builds the clients of the official GUI API, if the application sets the address of the
   * cockpit.
   *
   * @param restClients The builder of the support library of the cockpit.
   * @param address     The value of {@code loan-approval.cockpit.base-url}, if it is set.
   */
  public Cockpit(
      final RestClients restClients,
      @ConfigProperty(name = KEYS
          + ".base-url") final Optional<String> address) {

    this.tasklist = address.isPresent()
        ? restClients.build(OfficialTasklistApi.class, KEYS)
        : null;
    this.currentUser = address.isPresent()
        ? restClients.build(OfficialCurrentUserApi.class, KEYS)
        : null;

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

    if ((tasklist == null) || (cookie == null)) {
      return false;
    }

    try {

      final var assignee = Optional
          .ofNullable(tasklist.getUserTask(userTaskId, null))
          .map(UserTask::getAssignee)
          .map(Person::getId);
      if (assignee.isEmpty()) {
        return false;
      }

      final var user = Optional
          .ofNullable(currentUser.currentUser(null))
          .map(User::getId);
      return user.isPresent() && !assignee.get().equals(user.get());

    } catch (WebApplicationException | ProcessingException e) {
      // An error status, or no answer at all. Nothing says that somebody else took the task.
      return false;
    }

  }

}
