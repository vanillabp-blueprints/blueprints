package blueprint.simulator.esignature;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import blueprint.externalsystems.esignature.api.server.model.Envelope;

/**
 * The documents the simulated e-signature service holds, by the id the caller gave them. Held in
 * memory, like everything in this simulator. A document is kept as the generated
 * {@link Envelope} it arrived in, so the test API shows exactly what the service was given.
 */
@Component
public class Envelopes {

  private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();

  /**
   * Takes a document. A second document with the same id replaces the first, which is what a
   * caller that repeats a request expects.
   *
   * @param envelope The document.
   */
  public void add(
      final Envelope envelope) {

    envelopes.put(envelope.contractId(), envelope);

  }

  /**
   * A document by its id.
   *
   * @param contractId The id the caller gave the document.
   * @return The document, or empty if there is none of this id.
   */
  public Optional<Envelope> of(
      final String contractId) {

    return Optional.ofNullable(envelopes.get(contractId));

  }

}
