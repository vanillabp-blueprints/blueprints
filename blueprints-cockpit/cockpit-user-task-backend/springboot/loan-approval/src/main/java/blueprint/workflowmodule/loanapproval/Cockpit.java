package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.config.LoanApprovalAutoConfiguration.WORKFLOW_MODULE_ID;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
 * It asks with the cookie of the request. The cookie holds the login of the user who sent the
 * form, so the cockpit answers as that user. It answers two questions: who the user is
 * ({@code GET /gui/api/v1/app/current-user}), and whom the task is assigned to
 * ({@code GET /gui/api/v1/usertask/{id}}). The module needs no key and no user directory for
 * this, because the cockpit checks the login.
 * </p>
 *
 * <p>
 * The module asks only if the application sets the address of the cockpit, the key
 * {@value #ADDRESS}. Without it, and without a cookie, a form completes the task as before. So
 * the URLs in the log still work in a browser alone, and so does the dev shell, which has no
 * such interface. Every answer which is not clear is read the same way: nobody else took the
 * task. A form which is refused for no reason is worse than a form which is not refused.
 * </p>
 */
@Component
public class Cockpit {

  /** The key of the address of the cockpit, for example {@code http://localhost:8080}. */
  public static final String ADDRESS = WORKFLOW_MODULE_ID
      + ".cockpit.address";

  private final RestClient guiApi;

  /**
   * Builds the client of the interface of the cockpit, if the application sets its address.
   *
   * @param address The value of {@value #ADDRESS}, empty if it is not set.
   */
  public Cockpit(
      @Value("${"
          + ADDRESS
          + ":}") final String address) {

    this.guiApi = address.isBlank()
        ? null
        : RestClient.create(address.replaceAll("/+$", "")
            + "/gui/api/v1");

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

    if ((guiApi == null) || (cookie == null)) {
      return false;
    }

    final var assignee = read(cookie, "/usertask/{userTaskId}", userTaskId)
        .map(userTask -> userTask.get("assignee"))
        .map(Cockpit::idOf);
    if (assignee.isEmpty()) {
      return false;
    }

    final var user = read(cookie, "/app/current-user")
        .map(Cockpit::idOf);
    return user.isPresent() && !Objects.equals(assignee.get(), user.get());

  }

  /** The answer of the cockpit, or empty for every status other than 2xx. */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> read(
      final String cookie,
      final String path,
      final Object... variables) {

    return guiApi
        .get()
        .uri(path, variables)
        .header(HttpHeaders.COOKIE, cookie)
        .exchange((
            request,
            response) -> response.getStatusCode().is2xxSuccessful()
                ? Optional.ofNullable((Map<String, Object>) response.bodyTo(Map.class))
                : Optional.empty());

  }

  private static String idOf(
      final Object person) {

    return (person instanceof Map<?, ?> values) && (values.get("id") instanceof String id)
        ? id
        : null;

  }

}
