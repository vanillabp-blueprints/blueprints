package blueprint.externalsystems.esignature.rest.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;

/**
 * Where the e-signature service reaches this application, read from
 * {@code e-signature.callback-url}. The address of the e-signature service itself and the other
 * client keys below {@code e-signature.*} belong to the client module of the API.
 */
@ConfigMapping(prefix = "e-signature")
public interface ContractSigningProperties {

  /**
   * The address of this application's callback endpoint, as the e-signature service reaches it.
   * Behind a proxy that is not the address the application sees itself, so it is configured, not
   * guessed. It is optional here only so the adapter can say itself what is missing.
   *
   * @return The address.
   */
  Optional<String> callbackUrl();

}
