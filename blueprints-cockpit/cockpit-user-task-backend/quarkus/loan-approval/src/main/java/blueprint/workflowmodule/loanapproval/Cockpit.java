package blueprint.workflowmodule.loanapproval;

import static blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties.WORKFLOW_MODULE_ID;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

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
 *
 * <p>
 * The interface of the cockpit is {@link GuiApi}. The REST client of Quarkus implements it. The
 * client is built here and not injected, because the module may run without the address.
 * </p>
 */
@ApplicationScoped
public class Cockpit {

  /** The key of the address of the cockpit, for example {@code http://localhost:8080}. */
  public static final String ADDRESS = WORKFLOW_MODULE_ID
      + ".cockpit.address";

  /**
   * The two questions this module asks the cockpit. Every other status than 2xx is an answer as
   * well, so the client returns the response and does not throw.
   */
  public interface GuiApi {

    /**
     * Whom the user task is assigned to.
     *
     * @param userTaskId The id of the user task.
     * @param cookie     The {@code Cookie} header of the request.
     * @return The user task, with its assignee if it has one.
     */
    @GET
    @Path("/usertask/{userTaskId}")
    Response userTask(
        @PathParam("userTaskId") String userTaskId,
        @HeaderParam(HttpHeaders.COOKIE) String cookie);

    /**
     * Who the user of the cookie is.
     *
     * @param cookie The {@code Cookie} header of the request.
     * @return The user.
     */
    @GET
    @Path("/app/current-user")
    Response currentUser(
        @HeaderParam(HttpHeaders.COOKIE) String cookie);

  }

  private final GuiApi guiApi;

  /**
   * Builds the client of the interface of the cockpit, if the application sets its address.
   *
   * @param properties The keys of the module, among them {@value #ADDRESS}.
   */
  public Cockpit(
      final LoanApprovalProperties properties) {

    final var address = properties
        .cockpitAddress()
        .orElse("");
    this.guiApi = address.isBlank()
        ? null
        : QuarkusRestClientBuilder
            .newBuilder()
            .baseUri(URI.create(address.replaceAll("/+$", "")
                + "/gui/api/v1"))
            // Without this, the client throws for every status from 400 on.
            .property("microprofile.rest.client.disable.default.mapper", true)
            .build(GuiApi.class);

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

    final var assignee = read(guiApi.userTask(userTaskId, cookie))
        .map(userTask -> userTask.get("assignee"))
        .map(Cockpit::idOf);
    if (assignee.isEmpty()) {
      return false;
    }

    final var user = read(guiApi.currentUser(cookie))
        .map(Cockpit::idOf);
    return user.isPresent() && !Objects.equals(assignee.get(), user.get());

  }

  /** The answer of the cockpit, or empty for every status other than 2xx. */
  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> read(
      final Response response) {

    try (response) {
      return response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL
          ? Optional.ofNullable((Map<String, Object>) response.readEntity(Map.class))
          : Optional.empty();
    }

  }

  private static String idOf(
      final Object person) {

    return (person instanceof Map<?, ?> values) && (values.get("id") instanceof String id)
        ? id
        : null;

  }

}
