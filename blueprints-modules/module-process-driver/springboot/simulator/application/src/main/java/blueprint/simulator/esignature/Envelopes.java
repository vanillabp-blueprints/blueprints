package blueprint.simulator.esignature;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import blueprint.externalsystems.esignature.api.server.model.Envelope;
import blueprint.simulator.testapi.server.model.SentCallback;

/**
 * What the simulated e-signature service remembers, by the id the caller gave a document: the
 * documents it holds, how often it refused one, and the reports it sent about each. Held in
 * memory, like everything in this simulator. A document is kept as the generated
 * {@link Envelope} it arrived in, so the test API shows exactly what the service was given.
 */
@Component
public class Envelopes {

  private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();

  private final Map<String, AtomicInteger> refusals = new ConcurrentHashMap<>();

  private final Map<String, List<SentCallback>> callbacks = new ConcurrentHashMap<>();

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

  /**
   * Notes that the service refused a document.
   *
   * @param contractId The id the caller gave the document.
   */
  public void refused(
      final String contractId) {

    refusals
        .computeIfAbsent(contractId, id -> new AtomicInteger())
        .incrementAndGet();

  }

  /**
   * How often the service refused a document.
   *
   * @param contractId The id the caller gave the document.
   * @return The number of refused attempts.
   */
  public int refusalsOf(
      final String contractId) {

    final var count = refusals.get(contractId);
    return count == null
        ? 0
        : count.get();

  }

  /**
   * Notes a report the service sent.
   *
   * @param callback The report and the answer of the application.
   */
  public void sent(
      final SentCallback callback) {

    callbacks
        .computeIfAbsent(callback.contractId(), id -> new CopyOnWriteArrayList<>())
        .add(callback);

  }

  /**
   * The reports the service sent about a document.
   *
   * @param contractId The id of the document.
   * @return The reports in the order they were sent.
   */
  public List<SentCallback> callbacksOf(
      final String contractId) {

    return List.copyOf(callbacks.getOrDefault(contractId, List.of()));

  }

}
