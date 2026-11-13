package blueprint.simulator.esignature;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * The documents the simulated e-signature service holds, by the id the caller gave them. Held in
 * memory, like everything in this simulator.
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
