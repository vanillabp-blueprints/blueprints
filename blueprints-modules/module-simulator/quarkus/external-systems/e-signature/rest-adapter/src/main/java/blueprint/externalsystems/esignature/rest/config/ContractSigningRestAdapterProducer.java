package blueprint.externalsystems.esignature.rest.config;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import blueprint.externalsystems.esignature.callback.server.SignatureCallbackApi;
import blueprint.externalsystems.esignature.rest.ContractSigningRestAdapter;
import io.quarkus.runtime.Startup;
import io.vanillabp.cockpit.support.rest.restclient.RestClients;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link ContractSigning} bean and the callback endpoint, and does nothing
 * else.
 *
 * <p>
 * Quarkus finds this class, and the endpoint, through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class ContractSigningRestAdapterProducer {

  /** Where the keys of the client are, for example {@code e-signature.client.base-url}. */
  public static final String KEYS = "e-signature.client";

  /** The path of the callback endpoint, as the generated interface declares it. */
  private static final String CALLBACK_PATH = SignatureCallbackApi.class
      .getAnnotation(Path.class)
      .value();

  /**
   * The e-signature service, over REST. The support library of the Business Cockpit builds the
   * generated client from the keys below {@value #KEYS}: {@code base-url}, the timeouts, a
   * proxy, retries and the rest. It is created while the application starts, so a missing
   * address or callback address stops the start with a message instead of failing the first
   * contract.
   *
   * @param restClients The builder of the support library.
   * @param callbackUrl The value of {@code e-signature.callback-url}: the address of this
   *                    application's callback endpoint, as the e-signature service reaches it.
   *                    Behind a proxy that is not the address the application sees itself, so it
   *                    is configured, not guessed. Optional here only so the adapter can say itself
   *                    what is missing.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public ContractSigning contractSigning(
      final RestClients restClients,
      @ConfigProperty(name = "e-signature.callback-url") final Optional<String> callbackUrl) {

    final var callbackAddress = callbackUrl
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The e-signature service does not know where to report a signature. Set \
            'e-signature.callback-url' to the address of this application followed by '%s', as \
            the e-signature service reaches it. For a local run that is \
            http://localhost:8080%s."""
            .formatted(
                CALLBACK_PATH,
                CALLBACK_PATH)));

    return new ContractSigningRestAdapter(restClients.build(EnvelopesApi.class, KEYS), callbackAddress);

  }

}
