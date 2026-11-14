package blueprint.externalsystems.creditrating.rest.config;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where the credit rating is, read from {@code credit-rating.*}. The adapter brings the keys, the
 * application sets the values: the address of the simulator in the profile {@code local}, the
 * address of the real system in the profile {@code real}.
 */
@ConfigMapping(prefix = "credit-rating")
public interface CreditRatingProperties {

  /**
   * The address of the credit rating. There is no default, because no address is right for every
   * environment. It is optional here only so the adapter can say itself what is missing.
   *
   * @return The address.
   */
  Optional<String> baseUrl();

  /**
   * How long connecting and answering may take each. The user waits for this answer, so it is
   * short.
   *
   * @return The timeout.
   */
  @WithDefault("2s")
  Duration timeout();

}
