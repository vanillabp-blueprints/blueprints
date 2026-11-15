package blueprint.externalsystems.creditbureau.rest.config;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where the credit bureau is, and where it reaches this application, read from
 * {@code credit-bureau.*}. The adapter brings the keys, the application sets the values.
 */
@ConfigMapping(prefix = "credit-bureau")
public interface CreditBureauProperties {

  /**
   * The address of the credit bureau. There is no default, because no address is right for
   * every environment. It is optional here only so the adapter can say itself what is missing.
   *
   * @return The address.
   */
  Optional<String> baseUrl();

  /**
   * The address of this application's callback endpoint, as the credit bureau reaches it.
   * Behind a proxy that is not the address the application sees itself, so it is configured,
   * not guessed.
   *
   * @return The address.
   */
  Optional<String> callbackUrl();

  /**
   * How long connecting and answering may take each.
   *
   * @return The timeout.
   */
  @WithDefault("5s")
  Duration timeout();

}
