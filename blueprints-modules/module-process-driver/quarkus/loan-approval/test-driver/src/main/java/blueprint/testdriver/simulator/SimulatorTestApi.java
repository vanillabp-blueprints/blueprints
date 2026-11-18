package blueprint.testdriver.simulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.model.Envelope;
import blueprint.simulator.testapi.client.model.SentCallback;
import blueprint.simulator.testapi.client.model.SignatureStatus;
import blueprint.testdriver.GeneratedClients;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

/**
 * The client of the simulator's test API.
 *
 * <p>
 * The test API knows nothing about the workflow. It creates data in a simulated system and
 * makes a system act, for example sign a contract. Much of what a simulated system does is
 * chosen by the data it gets, for example by the name of a signer, so a test often needs no call
 * here at all. Which of these steps a loan approval needs,
 * and in which order, is known to the process driver alone. Keep it that way: a test API which
 * knows the process breaks with every change of the model.
 * </p>
 *
 * <p>
 * The client of the test API is generated from
 * {@code simulator/api/simulator-test-api.yaml}, the file the simulator implements, and the REST
 * client of Quarkus implements it. The REST client exists only while Quarkus runs, so a client is
 * made at its first call and not when this class is made: a test asks {@link #isRunning()} before
 * Quarkus starts. That is why the status is asked with the HTTP client of the JDK. What the
 * cockpit the simulator plays was told, a test reads through the client of the official GUI API
 * of the cockpit.
 * </p>
 *
 * <p>
 * A test finds the simulator through {@link #simulator()}, which reads the system property
 * {@code simulator.base-url}. The build sets it when it starts the simulator. Without it, the
 * simulator is expected at {@code http://localhost:8079}, where
 * {@code java -jar simulator/application/target/simulator-application-1.0.0-SNAPSHOT-runnable.jar}
 * starts it. The process driver is given the address
 * in its configuration and uses {@link #at(String)}.
 * </p>
 */
public final class SimulatorTestApi {

  /** Where a simulator started by hand answers. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8079";

  private final String baseUrl;

  private final GeneratedClients testApi;

  /** The client of each generated interface, made at its first call. */
  private final Map<Class<?>, Object> clients = new ConcurrentHashMap<>();

  private SimulatorTestApi(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    this.testApi = GeneratedClients.at(baseUrl);

  }

  /**
   * The client of the simulator this test run uses.
   *
   * @return The client.
   */
  public static SimulatorTestApi simulator() {

    return at(System.getProperty("simulator.base-url", DEFAULT_BASE_URL));

  }

  /**
   * The client of the simulator at the given address.
   *
   * @param baseUrl Where the simulator answers.
   * @return The client.
   */
  public static SimulatorTestApi at(
      final String baseUrl) {

    return new SimulatorTestApi(baseUrl);

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

    // The path of the generated StatusApi. Its client cannot be used here, see above.
    final var request = HttpRequest
        .newBuilder(URI.create(baseUrl
            + "/simulator/test-api/status"))
        .build();
    try (var http = HttpClient.newHttpClient()) {
      return "running".equals(http
          .send(request, HttpResponse.BodyHandlers.ofString())
          .body());
    } catch (IOException e) {
      // Nobody answers.
      return false;
    } catch (InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      return false;
    }

  }

  /**
   * Reserves a customer id at the credit rating which no other test uses.
   *
   * @return The customer id.
   */
  public String nextCustomerId() {

    return client(CreditRatingTestApi.class).nextId();

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

    client(CreditRatingTestApi.class).createRating(customerId, score);

  }

  /**
   * The document the e-signature service holds under an id.
   *
   * @param contractId The id the application gave the contract.
   * @return The document, as the e-signature service received it, or empty if there is none.
   */
  public Optional<Envelope> envelope(
      final String contractId) {

    try {
      return Optional.of(client(ESignatureTestApi.class).envelope(contractId));
    } catch (WebApplicationException e) {
      if (e
          .getResponse()
          .getStatus() == Status.NOT_FOUND.getStatusCode()) {
        return Optional.empty();
      }
      throw e;
    }

  }

  /**
   * Makes the e-signature service sign a contract and report it to the application, as the
   * service does after the customer signed.
   *
   * @param contractId The id the application gave the contract.
   * @return The report, and how the application answered it.
   */
  public SentCallback sign(
      final String contractId) {

    return client(ESignatureTestApi.class).sign(contractId);

  }

  /**
   * Makes the e-signature service send a report about a contract it does not hold, as a real
   * service may do.
   *
   * @param contractId  The id the report names.
   * @param status      What the report says.
   * @param callbackUrl Where the application takes reports.
   * @return The report, and how the application answered it.
   */
  public SentCallback sendCallback(
      final String contractId,
      final SignatureStatus status,
      final String callbackUrl) {

    return client(ESignatureTestApi.class).sendCallback(contractId, status, callbackUrl);

  }

  /**
   * The reports the e-signature service sent about a contract so far.
   *
   * @param contractId The id the application gave the contract.
   * @return The reports in the order they were sent.
   */
  public List<SentCallback> callbacks(
      final String contractId) {

    return client(ESignatureTestApi.class).callbacks(contractId);

  }

  /**
   * How often the e-signature service refused to take a contract.
   *
   * @param contractId The id the application gave the contract.
   * @return The number of refused attempts.
   */
  public int refusals(
      final String contractId) {

    return client(ESignatureTestApi.class).refusals(contractId);

  }

  private <A> A client(
      final Class<A> api) {

    return api.cast(clients.computeIfAbsent(api, testApi::of));

  }

}
