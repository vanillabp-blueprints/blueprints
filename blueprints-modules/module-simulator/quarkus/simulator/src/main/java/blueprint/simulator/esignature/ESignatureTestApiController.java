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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.externalsystems.esignature.api.server.model.Envelope;
import blueprint.externalsystems.esignature.callback.client.SignatureCallbackApi;
import blueprint.externalsystems.esignature.callback.client.model.Callback;

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

  private final Envelopes envelopes;

  private final RestClient.Builder restClients;

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
    this.restClients = restClients;

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
   *         document of this id. 502 if the application did not answer, or if the callback
   *         address does not end with the path of the callback API.
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

    final var callbackUrl = envelope
        .get()
        .callbackUrl();
    if (!callbackUrl.endsWith(SignatureCallbackApi.PATH_REPORT_SIGNATURE)) {
      return ResponseEntity
          .status(HttpStatus.BAD_GATEWAY)
          .body("Contract '"
              + contractId
              + "' signed, but its callback address "
              + callbackUrl
              + " does not end with "
              + SignatureCallbackApi.PATH_REPORT_SIGNATURE
              + ", the path of the callback API");
    }

    int answer;
    try {

      answer = callbackTo(callbackUrl)
          .reportSignature(new Callback(contractId, Callback.StatusEnum.SIGNED))
          .getStatusCode()
          .value();

    } catch (RestClientResponseException e) {

      // The application answered, but with an error, for example 404 for an unknown contract.
      answer = e
          .getStatusCode()
          .value();

    } catch (ResourceAccessException e) {

      log.warn(
          "Contract '{}' signed, but nobody answers at {}",
          contractId,
          callbackUrl);

      return ResponseEntity
          .status(HttpStatus.BAD_GATEWAY)
          .body("Contract '"
              + contractId
              + "' signed, but nobody answers at "
              + callbackUrl);

    }

    log.info(
        "Contract '{}' signed, reported to {}, which answered {}",
        contractId,
        callbackUrl,
        answer);

    return ResponseEntity.ok("Contract '"
        + contractId
        + "' signed. The application answered the callback with "
        + answer
        + ".");

  }

  /**
   * The generated client of the callback API, aimed at one callback address.
   *
   * <p>
   * The envelope carries the whole address of the callback. The generated client adds the path
   * of the callback API itself, so it gets the address without that path as its base.
   * </p>
   *
   * @param callbackUrl The callback address of an envelope. It ends with the path of the API.
   * @return The client.
   */
  private SignatureCallbackApi callbackTo(
      final String callbackUrl) {

    final var baseUrl = callbackUrl.substring(
        0,
        callbackUrl.length() - SignatureCallbackApi.PATH_REPORT_SIGNATURE.length());

    // The builder of Spring Boot is shared by every call, so each callback gets a copy of it.
    final var restClient = restClients
        .clone()
        .baseUrl(baseUrl)
        .build();

    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(SignatureCallbackApi.class);

  }

}
