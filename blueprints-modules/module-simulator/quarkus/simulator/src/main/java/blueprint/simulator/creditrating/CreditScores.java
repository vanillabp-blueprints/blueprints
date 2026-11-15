package blueprint.simulator.creditrating;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import blueprint.simulator.config.SimulatorProperties;

/**
 * What the simulated credit rating knows: a score per customer. It is held in memory, so a
 * restart of the simulator starts from the configured scores again.
 */
@Component
public class CreditScores {

  /**
   * The ids handed out by {@link #nextCustomerId()} start here, far away from the ids of the
   * configured customers.
   */
  private static final long FIRST_RESERVED_ID = 100_000;

  private final Map<String, Integer> scores = new ConcurrentHashMap<>();

  private final AtomicLong lastReservedId = new AtomicLong(FIRST_RESERVED_ID);

  /**
   * Creates the store with the configured scores.
   *
   * @param properties The data the simulator starts with.
   */
  public CreditScores(
      final SimulatorProperties properties) {

    scores.putAll(properties.creditScores());

  }

  /**
   * Reserves a customer id nobody used before. Two tests which run at the same time get two
   * different ids, so neither sees the data of the other.
   *
   * @return The reserved id.
   */
  public String nextCustomerId() {

    return "C-"
        + lastReservedId.incrementAndGet();

  }

  /**
   * Sets the score of a customer, replacing an earlier one.
   *
   * @param customerId The id of the customer.
   * @param score      The score.
   */
  public void set(
      final String customerId,
      final int score) {

    scores.put(customerId, score);

  }

  /**
   * The score of a customer.
   *
   * @param customerId The id of the customer.
   * @return The score, or empty if the customer is not known.
   */
  public Optional<Integer> of(
      final String customerId) {

    return Optional.ofNullable(scores.get(customerId));

  }

}
