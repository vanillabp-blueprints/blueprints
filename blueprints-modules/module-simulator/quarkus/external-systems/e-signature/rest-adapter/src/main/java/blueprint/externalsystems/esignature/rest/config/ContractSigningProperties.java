package blueprint.externalsystems.esignature.rest.config;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where the e-signature service is, and where it reaches this application, read from
 * {@code e-signature.*}. The adapter brings the keys, the application sets the values per
 * profile.
 */
@ConfigMapping(prefix = "e-signature")
public interface ContractSigningProperties {

  /**
   * The address of the e-signature service. There is no default, because no address is right
   * for every environment. It is optional here only so the adapter can say itself what is
   * missing.
   *
   * @return The address.
   */
  Optional<String> baseUrl();

  /**
   * The address of this application's callback endpoint, as the e-signature service reaches it.
   * Behind a proxy that is not the address the application sees itself, so it is configured, not
   * guessed.
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
