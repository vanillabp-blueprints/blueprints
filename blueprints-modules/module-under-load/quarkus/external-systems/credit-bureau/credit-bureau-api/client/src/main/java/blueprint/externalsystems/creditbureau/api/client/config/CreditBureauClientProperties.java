package blueprint.externalsystems.creditbureau.api.client.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where the credit bureau is and how the client talks to it, read from {@code credit-bureau.*}. The
 * keys have the names of the REST client keys of the Business Cockpit support, so they stay the
 * same once the support has a Quarkus module.
 */
@ConfigMapping(prefix = "credit-bureau")
public interface CreditBureauClientProperties {

  /**
   * The address of the credit bureau. There is no default, because no address is right for every
   * environment. It is optional here only so the producer can say itself what is missing.
   *
   * @return The address.
   */
  Optional<String> baseUrl();

  /**
   * How long the client waits for a connection, in milliseconds.
   *
   * @return The timeout.
   */
  @WithDefault("5000")
  int connectTimeout();

  /**
   * How long the client waits for the answer once it is connected, in milliseconds.
   *
   * @return The timeout.
   */
  @WithDefault("5000")
  int readTimeout();

}
