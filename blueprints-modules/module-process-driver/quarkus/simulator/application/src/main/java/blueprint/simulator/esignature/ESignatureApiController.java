package blueprint.simulator.esignature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import blueprint.externalsystems.esignature.api.server.EnvelopesApi;
import blueprint.externalsystems.esignature.api.server.model.Envelope;
import blueprint.simulator.testapi.server.model.SignatureStatus;

/**
 * The API of the e-signature service. The path, the method and the JSON come from
 * {@link EnvelopesApi}, the interface generated from
 * {@code external-systems/e-signature/e-signature-api/e-signature.yaml}. This class only
 * implements it. The adapter of the application talks to this exactly as it talks to the real
 * system.
 *
 * <p>
 * The real service waits for a person to sign. The simulator chooses what happens by the name of
 * the signer, see {@link SignerBehaviour}. Every other signer waits for the test API: nothing is
 * signed until a test or a person calls {@code sign}. So the callback comes when the caller wants
 * it, and no test depends on a clock.
 * </p>
 */
@RestController
public class ESignatureApiController implements EnvelopesApi {

  private static final Logger log = LoggerFactory.getLogger(ESignatureApiController.class);

  private final Envelopes envelopes;

  private final SignatureReports reports;

  /**
   * Creates the controller.
   *
   * @param envelopes What the simulated service remembers.
   * @param reports   Sends the reports of the service.
   */
  public ESignatureApiController(
      final Envelopes envelopes,
      final SignatureReports reports) {

    this.envelopes = envelopes;
    this.reports = reports;

  }

  /**
   * Takes a document for signing.
   *
   * @param envelope The document, and where to report its signature.
   * @return 202, because the signature comes later.
   * @throws ResponseStatusException With 500, if the signer is the one who breaks the service.
   */
  @Override
  public ResponseEntity<Void> createEnvelope(
      final Envelope envelope) {

    final var behaviour = SignerBehaviour.of(envelope.signer());
    if (behaviour == SignerBehaviour.BREAKS_THE_SERVICE) {
      envelopes.refused(envelope.contractId());
      log.info(
          "Contract '{}' refused with an error, because its signer is '{}'",
          envelope.contractId(),
          envelope.signer());
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The e-signature service failed");
    }

    envelopes.add(envelope);

    switch (behaviour) {
      case SIGNS -> reports.sendInAMoment(envelope.contractId(), SignatureStatus.SIGNED, envelope.callbackUrl());
      case DECLINES -> reports.sendInAMoment(envelope.contractId(), SignatureStatus.DECLINED, envelope.callbackUrl());
      case NEVER_ANSWERS -> log.info(
          "Contract '{}' waits for '{}', who will never sign it",
          envelope.contractId(),
          envelope.signer());
      default -> log.info(
          "Contract '{}' waits for the signature of '{}'. Sign it -> {}/simulator/test-api/e-signature/sign?contractId={}",
          envelope.contractId(),
          envelope.signer(),
          ServletUriComponentsBuilder
              .fromCurrentContextPath()
              .toUriString(),
          envelope.contractId());
    }

    return ResponseEntity
        .accepted()
        .build();

  }

}
