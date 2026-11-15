package blueprint.externalsystems.creditbureau.rest.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

/**
 * Where the credit bureau reaches this application, read from
 * {@code credit-bureau.callback-url}. The address of the credit bureau itself and the other
 * client keys below {@code credit-bureau.*} belong to the client module of the API.
 */
@ConfigMapping(prefix = "credit-bureau")
public interface CreditBureauProperties {

  /**
   * The address of this application's callback endpoint, as the credit bureau reaches it. Behind
   * a proxy that is not the address the application sees itself, so it is configured, not
   * guessed. It is optional here only so the adapter can say itself what is missing.
   *
   * @return The address.
   */
  Optional<String> callbackUrl();

}
