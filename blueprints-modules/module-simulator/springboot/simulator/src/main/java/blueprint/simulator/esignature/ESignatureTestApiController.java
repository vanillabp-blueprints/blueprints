package blueprint.simulator.esignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The test API of the simulated e-signature service. It shows what the service was given, and it
 * makes the service do what the real one does when a person signs. It knows nothing about loans
 * or workflows.
 *
 * <p>
 * Every action is a GET, so a person can use it in a browser as well, like the API of the
 * application. A test uses the same URLs.
 * </p>
 */
@RestController
@RequestMapping("/simulator/test-api/e-signature")
public class ESignatureTestApiController {

  private static final Logger log = LoggerFactory.getLogger(ESignatureTestApiController.class);

  /** What the service reports, in its own words. */
  record Callback(
                  String contractId,
                  String status) {
  }

  private final Envelopes envelopes;

  private final RestClient callbacks;

  /**
   * Creates the controller.
   *
   * @param envelopes   The documents the simulated service holds.
   * @param restClients The REST client builder of Spring Boot, for the callbacks.
   */
  public ESignatureTestApiController(
      final Envelopes envelopes,
      final RestClient.Builder restClients) {

    this.envelopes = envelopes;
    this.callbacks = restClients.build();

  }

  /**
   * The document the service holds under an id.
   *
   * @param contractId The id the caller gave the document.
   * @return The document, or 404 if the service holds none of this id.
   */
  @GetMapping("/envelope")
  public ResponseEntity<Envelope> envelope(
      @RequestParam final String contractId) {

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
   * @return The status the application answered the callback with. 404 if the service holds no
   *         document of this id, 502 if the application did not answer.
   */
  @GetMapping("/sign")
  public ResponseEntity<String> sign(
      @RequestParam final String contractId) {

    final var envelope = envelopes.of(contractId);
    if (envelope.isEmpty()) {
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body("No contract '"
              + contractId
              + "' waits for a signature");
    }

    try {

      final var answer = callbacks
          .post()
          .uri(envelope
              .get()
              .callbackUrl())
          .body(new Callback(contractId, "SIGNED"))
          .exchange((
              request,
              response) -> response
                  .getStatusCode()
                  .value());

      log.info(
          "Contract '{}' signed, reported to {}, which answered {}",
          contractId,
          envelope
              .get()
              .callbackUrl(),
          answer);

      return ResponseEntity.ok("Contract '"
          + contractId
          + "' signed. The application answered the callback with "
          + answer
          + ".");

    } catch (ResourceAccessException e) {

      log.warn(
          "Contract '{}' signed, but nobody answers at {}",
          contractId,
          envelope
              .get()
              .callbackUrl());

      return ResponseEntity
          .status(HttpStatus.BAD_GATEWAY)
          .body("Contract '"
              + contractId
              + "' signed, but nobody answers at "
              + envelope
                  .get()
                  .callbackUrl());

    }

  }

}
