package blueprint.workflowmodule.loanapproval;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The client of the simulator's test API, and of the cockpit API the simulator plays.
 *
 * <p>
 * The test API knows nothing about the workflow. It creates data in a simulated system and
 * makes a system act, for example sign a contract. Which of these steps a loan approval needs,
 * and in which order, is known to the test alone. Keep it that way: a test API which knows the
 * process breaks with every change of the model.
 * </p>
 *
 * <p>
 * Where the simulator runs comes from the system property {@code simulator.base-url}. The build
 * sets it when it starts the simulator. Without it, the simulator is expected at
 * {@code http://localhost:8079}, where {@code mvn -pl simulator spring-boot:run} starts it.
 * </p>
 */
public final class SimulatorTestApi {

  /** Where a simulator started by hand answers. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8079";

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private final String baseUrl;

  private final RestClient testApi;

  private final RestClient cockpit;

  private SimulatorTestApi(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    this.testApi = RestClient.create(baseUrl
        + "/simulator/test-api");
    this.cockpit = RestClient.create(baseUrl
        + "/official-api/v1");

  }

  /**
   * The client of the simulator this test run uses.
   *
   * @return The client.
   */
  public static SimulatorTestApi simulator() {

    return new SimulatorTestApi(System.getProperty("simulator.base-url", DEFAULT_BASE_URL));

  }

  /**
   * Where the simulator answers. The adapters of the application are pointed below this.
   *
   * @return The address of the simulator.
   */
  public String baseUrl() {

    return baseUrl;

  }

  /**
   * Whether the simulator answers at all.
   *
   * @return {@code true} if it does.
   */
  public boolean isRunning() {

    try {
      return "running".equals(testApi
          .get()
          .uri("/status")
          .retrieve()
          .body(String.class));
    } catch (ResourceAccessException e) {
      return false;
    }

  }

  /**
   * Reserves a customer id at the credit rating which no other test uses.
   *
   * @return The customer id.
   */
  public String nextCustomerId() {

    return testApi
        .get()
        .uri("/credit-rating/next-id")
        .retrieve()
        .body(String.class);

  }

  /**
   * Makes the credit rating know a customer.
   *
   * @param customerId The id of the customer.
   * @param score      The score the credit rating answers for this customer.
   */
  public void createRating(
      final String customerId,
      final int score) {

    testApi
        .get()
        .uri("/credit-rating/create-rating?customerId={customerId}&score={score}", customerId, score)
        .retrieve()
        .toBodilessEntity();

  }

  /**
   * The document the e-signature service holds under an id.
   *
   * @param contractId The id the application gave the contract.
   * @return The document, as the e-signature service received it, or empty if there is none.
   */
  @SuppressWarnings("unchecked")
  public Optional<Map<String, Object>> envelope(
      final String contractId) {

    return testApi
        .get()
        .uri("/e-signature/envelope?contractId={contractId}", contractId)
        .exchange((
            request,
            response) -> response.getStatusCode().is2xxSuccessful()
                ? Optional.ofNullable((Map<String, Object>) response.bodyTo(Map.class))
                : Optional.empty());

  }

  /**
   * Makes the e-signature service sign a contract and report it to the application.
   *
   * @param contractId The id the application gave the contract.
   * @return What the simulator answered: its status and its text, which names the status the
   *         application answered the callback with.
   */
  public String sign(
      final String contractId) {

    return testApi
        .get()
        .uri("/e-signature/sign?contractId={contractId}", contractId)
        .exchange((
            request,
            response) -> response
                .getStatusCode()
                .value()
                + " "
                + response.bodyTo(String.class));

  }

  /**
   * Waits until the cockpit knows a user task and the condition holds. The cockpit adapter
   * reports through the outbox, so a report arrives a moment after the event which caused it.
   *
   * @param userTaskId The id of the user task.
   * @param condition  What the cockpit is expected to know about the task.
   * @return The user task, as the official API of the cockpit returns it.
   */
  public Map<String, Object> awaitUserTask(
      final String userTaskId,
      final Predicate<Map<String, Object>> condition) {

    final var lastSeen = new AtomicReference<Optional<Map<String, Object>>>(Optional.empty());

    try {

      await()
          .atMost(TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            final var found = userTask(userTaskId);
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The cockpit did not get the expected report of user task '"
              + userTaskId
              + "' within "
              + TIMEOUT
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("nothing - the task was never reported"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> userTask(
      final String userTaskId) {

    return cockpit
        .get()
        .uri("/usertask/{userTaskId}", userTaskId)
        // The simulator answers a task it does not know with an error. That only means the
        // report has not arrived yet.
        .exchange((
            request,
            response) -> response.getStatusCode().is2xxSuccessful()
                ? Optional.ofNullable((Map<String, Object>) response.bodyTo(Map.class))
                : Optional.empty());

  }

}
