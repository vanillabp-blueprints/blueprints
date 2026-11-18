package blueprint.testdriver.simulator;


import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.StatusApi;
import blueprint.simulator.testapi.client.model.Envelope;
import blueprint.simulator.testapi.client.model.SentCallback;
import blueprint.simulator.testapi.client.model.SignatureStatus;
import blueprint.testdriver.GeneratedClients;

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
 * {@code simulator/api/simulator-test-api.yaml}, the file the simulator implements. What the
 * cockpit the simulator plays was told, a test reads through the client of the official GUI API
 * of the cockpit.
 * </p>
 *
 * <p>
 * A test finds the simulator through {@link #simulator()}, which reads the system property
 * {@code simulator.base-url}. The build sets it when it starts the simulator. Without it, the
 * simulator is expected at {@code http://localhost:8079}, where
 * {@code mvn -pl simulator/application spring-boot:run} starts it. The process driver is given the address
 * in its configuration and uses {@link #at(String)}.
 * </p>
 */
public final class SimulatorTestApi {

  /** Where a simulator started by hand answers. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8079";

  private final String baseUrl;

  private final StatusApi status;

  private final CreditRatingTestApi creditRating;

  private final ESignatureTestApi eSignature;

  private SimulatorTestApi(
      final String baseUrl) {

    this.baseUrl = baseUrl;
    final var testApi = GeneratedClients.at(baseUrl);
    this.status = testApi.of(StatusApi.class);
    this.creditRating = testApi.of(CreditRatingTestApi.class);
    this.eSignature = testApi.of(ESignatureTestApi.class);

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

    try {
      return "running".equals(status.status());
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

    return creditRating.nextId();

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

    creditRating.createRating(customerId, score);

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
      return Optional.of(eSignature.envelope(contractId));
    } catch (RestClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
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

    return eSignature.sign(contractId);

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

    return eSignature.sendCallback(contractId, status, callbackUrl);

  }

  /**
   * The reports the e-signature service sent about a contract so far.
   *
   * @param contractId The id the application gave the contract.
   * @return The reports in the order they were sent.
   */
  public List<SentCallback> callbacks(
      final String contractId) {

    return eSignature.callbacks(contractId);

  }

  /**
   * How often the e-signature service refused to take a contract.
   *
   * @param contractId The id the application gave the contract.
   * @return The number of refused attempts.
   */
  public int refusals(
      final String contractId) {

    return eSignature.refusals(contractId);

  }

}
