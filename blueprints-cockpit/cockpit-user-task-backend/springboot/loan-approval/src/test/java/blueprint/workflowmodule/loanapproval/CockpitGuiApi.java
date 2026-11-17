package blueprint.workflowmodule.loanapproval;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The two answers of the official GUI API of the Business Cockpit which {@link Cockpit} asks
 * for: who is logged in, and whom a user task is assigned to.
 *
 * <p>
 * The dev shell simulator has no such interface, so the test brings this small server. It knows
 * a user by the cookie {@code bc=<user id>}. The cockpit itself checks the login token in that
 * cookie, but that is the business of the cockpit, not of the workflow module. A test assigns a
 * task with {@link #assign(String, String)}, which is what a user does in the cockpit when they
 * take the task over.
 * </p>
 */
public final class CockpitGuiApi {

  private static CockpitGuiApi running;

  private final HttpServer server;

  private final Map<String, String> assignees = new ConcurrentHashMap<>();

  private CockpitGuiApi() throws IOException {

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/gui/api/v1/app/current-user", this::currentUser);
    server.createContext("/gui/api/v1/usertask/", this::userTask);
    server.start();
    // The server belongs to this test run and must not outlive it.
    Runtime
        .getRuntime()
        .addShutdownHook(new Thread(() -> server.stop(0)));

  }

  /**
   * The server of this test run. The first call starts it.
   *
   * @return The running server.
   */
  public static synchronized CockpitGuiApi start() {

    if (running == null) {
      try {
        running = new CockpitGuiApi();
      } catch (IOException e) {
        throw new IllegalStateException("Cannot start the stand-in of the cockpit", e);
      }
    }
    return running;

  }

  /**
   * The address of the official GUI API, which is {@code loan-approval.cockpit.base-url}.
   *
   * @return The address of this server, with the path of the API.
   */
  public String baseUrl() {

    return "http://localhost:"
        + server
            .getAddress()
            .getPort()
        + "/gui/api/v1";

  }

  /**
   * A user takes the task over in the cockpit.
   *
   * @param userTaskId The id of the user task.
   * @param user       The id of the user who takes it.
   */
  public void assign(
      final String userTaskId,
      final String user) {

    assignees.put(userTaskId, user);

  }

  private void currentUser(
      final HttpExchange exchange) throws IOException {

    final var cookie = exchange
        .getRequestHeaders()
        .getFirst("Cookie");
    if ((cookie == null) || !cookie.startsWith("bc=")) {
      answer(exchange, 401, "");
      return;
    }
    answer(exchange, 200, "{\"id\":\""
        + cookie.substring("bc=".length())
        + "\"}");

  }

  private void userTask(
      final HttpExchange exchange) throws IOException {

    final var userTaskId = exchange
        .getRequestURI()
        .getPath()
        .substring("/gui/api/v1/usertask/".length());
    final var assignee = assignees.get(userTaskId);
    answer(exchange, 200, assignee == null
        ? "{\"id\":\""
            + userTaskId
            + "\"}"
        : "{\"id\":\""
            + userTaskId
            + "\",\"assignee\":{\"id\":\""
            + assignee
            + "\"}}");

  }

  private static void answer(
      final HttpExchange exchange,
      final int status,
      final String json) throws IOException {

    final var body = json.getBytes(StandardCharsets.UTF_8);
    exchange
        .getResponseHeaders()
        .add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    try (var out = exchange.getResponseBody()) {
      out.write(body);
    }

  }

}
