package blueprint.simulator.esignature;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import blueprint.externalsystems.esignature.api.server.model.Envelope;
import blueprint.simulator.testapi.server.ESignatureTestApi;
import blueprint.simulator.testapi.server.model.SentCallback;

/**
 * The test API of the simulated e-signature service. It shows what the service was given, and it
 * makes the service do what the real one does when a person signs. It knows nothing about loans
 * or workflows.
 *
 * <p>
 * Every action is a GET, so a person can use it in a browser as well, like the API of the
 * application. A test uses the same URLs.
 * </p>
 *
 * <p>
 * The paths and the parameters come from {@link ESignatureTestApi}, the interface generated from
 * {@code simulator/api/simulator-test-api.yaml}. This class only implements it.
 * </p>
 */
@RestController
public class ESignatureTestApiController implements ESignatureTestApi {

  private final Envelopes envelopes;

  private final SignatureReports reports;

  /**
   * Creates the controller.
   *
   * @param envelopes The documents the simulated service holds.
   * @param reports   Sends the reports of the service.
   */
  public ESignatureTestApiController(
      final Envelopes envelopes,
      final SignatureReports reports) {

    this.envelopes = envelopes;
    this.reports = reports;

  }

  /**
   * The document the service holds under an id.
   *
   * @param contractId The id the caller gave the document.
   * @return The document, or 404 if the service holds none of this id.
   */
  @Override
  public ResponseEntity<Envelope> envelope(
      final String contractId) {

    return ResponseEntity.of(envelopes.of(contractId));

  }

  /**
   * Signs a document and reports it to the callback address of its envelope, as the real service
   * does after the person signed.
   *
   * <p>
   * The callback runs before this method returns. A real service calls back later, but the
   * receiver cannot tell the difference, and the caller of this method learns at once whether
   * the application took the report.
   * </p>
   *
   * @param contractId The id the caller gave the document.
   * @return The report and the status the application answered with. 404 if the service holds
   *         no document of this id. 502 if the report could not be delivered.
   */
  @Override
  public ResponseEntity<SentCallback> sign(
      final String contractId) {

    final var envelope = envelopes.of(contractId);
    if (envelope.isEmpty()) {
      throw new NoSuchContractException(contractId);
    }

    return ResponseEntity.ok(reports.sendSigned(
        contractId,
        envelope
            .get()
            .callbackUrl()));

  }

  /**
   * Reports a signature as the service does, also for a document the service does not hold.
   *
   * @param contractId  The id of the document the report names.
   * @param callbackUrl Where to send the report.
   * @return The report and the status the application answered with. 502 if the report could
   *         not be delivered.
   */
  @Override
  public ResponseEntity<SentCallback> sendCallback(
      final String contractId,
      final String callbackUrl) {

    return ResponseEntity.ok(reports.sendSigned(contractId, callbackUrl));

  }

  /**
   * The service holds no document of the id a test asked to sign.
   *
   * @param e The exception which names the id.
   * @return The reason, with status 404.
   */
  @ExceptionHandler(NoSuchContractException.class)
  public ResponseEntity<String> noSuchContract(
      final NoSuchContractException e) {

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.TEXT_PLAIN)
        .body(e.getMessage());

  }

  /**
   * A report could not be delivered.
   *
   * @param e The exception which says why.
   * @return The reason, with status 502.
   */
  @ExceptionHandler(CallbackNotDeliveredException.class)
  public ResponseEntity<String> callbackNotDelivered(
      final CallbackNotDeliveredException e) {

    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.TEXT_PLAIN)
        .body(e.getMessage());

  }

  /** A test asked to sign a document the service does not hold. */
  static class NoSuchContractException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    NoSuchContractException(
        final String contractId) {

      super("No contract '"
          + contractId
          + "' waits for a signature");

    }

  }

}
