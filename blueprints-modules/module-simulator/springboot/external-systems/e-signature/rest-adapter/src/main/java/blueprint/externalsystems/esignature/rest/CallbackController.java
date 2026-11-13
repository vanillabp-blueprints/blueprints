package blueprint.externalsystems.esignature.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blueprint.externalsystems.esignature.SignedContracts;

/**
 * Where the e-signature service reports a signature. It is the second half of the adapter: the
 * service calls this endpoint, and the endpoint hands the news to {@link SignedContracts}, which
 * the application implements.
 *
 * <p>
 * The endpoint is a POST with a JSON body, because the e-signature service decides what its
 * callback looks like. The rule that a blueprint has GET requests only is about the API a person
 * uses in a browser, and nobody types a callback.
 * </p>
 */
@RestController
@RequestMapping(CallbackController.PATH)
public class CallbackController {

  /** The path of this endpoint. The application adds its own address in front. */
  public static final String PATH = "/e-signature/callback";

  /** What the e-signature service sends, in its own words. */
  record Callback(
                  String contractId,
                  String status) {
  }

  private final SignedContracts signedContracts;

  /**
   * Creates the controller.
   *
   * @param signedContracts The application, as far as it wants to hear about signatures.
   */
  public CallbackController(
      final SignedContracts signedContracts) {

    this.signedContracts = signedContracts;

  }

  /**
   * Takes one report of the e-signature service.
   *
   * @param callback The report.
   * @return 204 if the application took it, 404 if it does not know the contract, 400 for a
   *         status this adapter does not know.
   */
  @PostMapping
  public ResponseEntity<Void> reported(
      @RequestBody final Callback callback) {

    if (!"SIGNED".equals(callback.status())) {
      return ResponseEntity
          .badRequest()
          .build();
    }

    return signedContracts.signed(callback.contractId())
        ? ResponseEntity
            .noContent()
            .build()
        : ResponseEntity
            .notFound()
            .build();

  }

}
