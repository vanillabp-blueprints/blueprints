package blueprint.loaddriver;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A duration which differs a little every time, the way think times and answers of other
 * systems do.
 */
final class Spread {

  private Spread() {
  }

  /**
   * A duration somewhere between {@code average - spread} and {@code average + spread}, and
   * never below zero.
   *
   * @param average The average.
   * @param spread  How far a single value may be off the average.
   * @return One value.
   */
  static Duration around(
      final Duration average,
      final Duration spread) {

    final var offset = spread.isZero()
        ? 0
        : ThreadLocalRandom
            .current()
            .nextLong(-spread.toMillis(), spread.toMillis() + 1);
    return Duration.ofMillis(Math.max(0, average.toMillis() + offset));

  }

}
