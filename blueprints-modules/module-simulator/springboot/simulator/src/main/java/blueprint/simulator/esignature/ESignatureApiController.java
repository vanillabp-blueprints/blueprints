package blueprint.simulator.esignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The API of the e-signature service, as {@code external-systems/e-signature/openapi.yaml}
 * describes it. The adapter of the application talks to this exactly as it talks to the real
 * system.
 *
 * <p>
 * The real service waits for a person to sign. The simulator waits for its test API instead:
 * nothing is signed until a test or a person calls {@code sign}. So the callback comes when the
 * caller wants it, and no test depends on a clock.
 * </p>
 */
@RestController
@RequestMapping("/e-signature/api/v1")
public class ESignatureApiController {

  private static final Logger log = LoggerFactory.getLogger(ESignatureApiController.class);

  private final Envelopes envelopes;

  /**
   * Creates the controller.
   *
   * @param envelopes The documents the simulated service holds.
   */
  public ESignatureApiController(
      final Envelopes envelopes) {

    this.envelopes = envelopes;

  }

  /**
   * Takes a document for signing.
   *
   * @param envelope The document, and where to report its signature.
   * @return 202, because the signature comes later.
   */
  @PostMapping("/envelopes")
  public ResponseEntity<Void> createEnvelope(
      @RequestBody final Envelope envelope) {

    envelopes.add(envelope);

    log.info(
        "Contract '{}' waits for the signature of '{}'. Sign it -> {}/simulator/test-api/e-signature/sign?contractId={}",
        envelope.contractId(),
        envelope.signer(),
        ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .toUriString(),
        envelope.contractId());

    return ResponseEntity
        .accepted()
        .build();

  }

}
