package blueprint.workflowmodule.loanapproval;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stands between the workflow module and the dev shell simulator, passes every request on, and
 * keeps a copy of every report about a user task or a workflow. So the test sees exactly what
 * the module reported, including the values the simulator does not keep.
 *
 * <p>
 * This is test equipment, not part of the pattern. A real application reports to the cockpit
 * directly.
 * </p>
 */
public final class ReportRecorder {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  /** The request headers which are passed on. The others are set by the HTTP client itself. */
  private static final Set<String> PASSED_HEADERS = Set.of("content-type", "accept", "authorization");

  private static final JsonMapper JSON = JsonMapper
      .builder()
      .build();

  private final String baseUrl;

  private final String target;

  private final HttpClient client = HttpClient.newHttpClient();

  private final List<Report> reports = new CopyOnWriteArrayList<>();

  /**
   * One report as the module sent it.
   *
   * @param path The path it was sent to, such as {@code .../usertask/created}.
   * @param body The report.
   */
  public record Report(
                       String path,
                       Map<String, Object> body) {

    /**
     * Whether the report is about the creation, an update, the completion or the cancellation.
     *
     * @param kind The last segment of the path: {@code created}, {@code updated},
     *             {@code completed} or {@code cancelled}.
     * @return True if the report is of this kind.
     */
    public boolean is(
        final String kind) {

      return path.endsWith("/"
          + kind);

    }

  }

  private ReportRecorder(
      final String target) {

    this.target = target;
    final var port = CockpitSimulator.freePort();
    this.baseUrl = "http://localhost:"
        + port;

    try {
      final var server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
      server.createContext("/", this::passOn);
      server.start();
      Runtime
          .getRuntime()
          .addShutdownHook(new Thread(() -> server.stop(0)));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start the report recorder", e);
    }

  }

  /**
   * Starts a recorder which passes every request on to the given address.
   *
   * @param target The address of the dev shell simulator.
   * @return The running recorder.
   */
  public static ReportRecorder inFrontOf(
      final String target) {

    return new ReportRecorder(target);

  }

  /**
   * The address a workflow module reports to, which is {@code vanillabp.cockpit.rest.base-url}.
   *
   * @return The address of the recorder.
   */
  public String baseUrl() {

    return baseUrl;

  }

  /**
   * Waits for the latest report of the given kind about one user task, and for it to meet the
   * condition. The cockpit adapter reports through the outbox, so a report arrives a moment
   * after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param kind       {@code created}, {@code updated}, {@code completed} or {@code cancelled}.
   * @param condition  What the report is expected to say.
   * @return The report.
   */
  public Map<String, Object> awaitUserTaskReport(
      final String userTaskId,
      final String kind,
      final Predicate<Map<String, Object>> condition) {

    return awaitReport(
        "user task '"
            + userTaskId
            + "'",
        report -> report
            .path()
            .contains("/usertask/") && report.is(kind) && userTaskId.equals(report
                .body()
                .get("userTaskId")),
        condition);

  }

  /**
   * Waits for the latest report of the given kind about the workflow of one business case.
   *
   * @param businessId The id of the workflow aggregate.
   * @param kind       {@code created}, {@code updated}, {@code completed} or {@code cancelled}.
   * @param condition  What the report is expected to say.
   * @return The report.
   */
  public Map<String, Object> awaitWorkflowReport(
      final String businessId,
      final String kind,
      final Predicate<Map<String, Object>> condition) {

    return awaitReport(
        "workflow '"
            + businessId
            + "'",
        report -> report
            .path()
            .contains("/workflow/") && report.is(kind) && businessId.equals(report
                .body()
                .get("businessId")),
        condition);

  }

  private Map<String, Object> awaitReport(
      final String about,
      final Predicate<Report> which,
      final Predicate<Map<String, Object>> condition) {

    try {

      return await()
          .atMost(TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> latest(which), found -> found
              .filter(condition)
              .isPresent())
          .orElseThrow();

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The cockpit did not get the expected report about "
              + about
              + " within "
              + TIMEOUT
              + ". Last seen: "
              + latest(which)
                  .map(Object::toString)
                  .orElse("nothing"), e);

    }

  }

  private Optional<Map<String, Object>> latest(
      final Predicate<Report> which) {

    return reports
        .stream()
        .filter(which)
        .reduce((
            first,
            second) -> second)
        .map(Report::body);

  }

  private void passOn(
      final HttpExchange exchange) throws IOException {

    try (exchange) {

      final var body = exchange
          .getRequestBody()
          .readAllBytes();
      final var path = exchange
          .getRequestURI()
          .toString();
      if ("POST".equals(exchange.getRequestMethod()) && (path.contains("/usertask/") || path.contains("/workflow/"))) {
        reports.add(new Report(path, JSON.readValue(body, new TypeReference<Map<String, Object>>() {
        })));
      }

      final var request = HttpRequest
          .newBuilder(URI.create(target + path))
          .method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(body));
      exchange
          .getRequestHeaders()
          .forEach((
              name,
              values) -> {
            if (PASSED_HEADERS.contains(name.toLowerCase())) {
              values.forEach(value -> request.header(name, value));
            }
          });

      final var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      response
          .headers()
          .firstValue("content-type")
          .ifPresent(type -> exchange
              .getResponseHeaders()
              .add("Content-Type", type));
      final var answer = response.body();
      exchange.sendResponseHeaders(response.statusCode(), answer.length == 0 ? -1 : answer.length);
      if (answer.length > 0) {
        exchange
            .getResponseBody()
            .write(answer);
      }

    } catch (InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      throw new IOException(e);
    }

  }

}
