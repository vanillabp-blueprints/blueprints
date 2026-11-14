package blueprint.simulator.esignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import blueprint.externalsystems.esignature.api.server.EnvelopesApi;
import blueprint.externalsystems.esignature.api.server.model.Envelope;

/**
 * The API of the e-signature service. The path, the method and the JSON come from
 * {@link EnvelopesApi}, the interface generated from
 * {@code external-systems/e-signature/e-signature-api/e-signature.yaml}. This class only
 * implements it. The adapter of the application talks to this exactly as it talks to the real
 * system.
 *
 * <p>
 * The real service waits for a person to sign. The simulator waits for its test API instead:
 * nothing is signed until a test or a person calls {@code sign}. So the callback comes when the
 * caller wants it, and no test depends on a clock.
 * </p>
 */
@RestController
public class ESignatureApiController implements EnvelopesApi {

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
  @Override
  public ResponseEntity<Void> createEnvelope(
      final Envelope envelope) {

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
