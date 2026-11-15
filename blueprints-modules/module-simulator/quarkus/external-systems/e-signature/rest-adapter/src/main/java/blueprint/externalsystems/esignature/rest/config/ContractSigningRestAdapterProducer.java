package blueprint.externalsystems.esignature.rest.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import blueprint.externalsystems.esignature.callback.server.SignatureCallbackApi;
import blueprint.externalsystems.esignature.rest.ContractSigningRestAdapter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.Startup;
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

  /** The path of the callback endpoint, as the generated interface declares it. */
  private static final String CALLBACK_PATH = SignatureCallbackApi.class
      .getAnnotation(Path.class)
      .value();

  /**
   * The e-signature service, over REST. Created while the application starts, so a missing
   * address stops the start with a message instead of failing the first contract.
   *
   * @param properties Where the e-signature service is, and where it calls back.
   * @return The adapter.
   */
  @Produces
  @Singleton
  @Startup
  public ContractSigning contractSigning(
      final ContractSigningProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The e-signature service has no address. Set 'e-signature.base-url' to where it \
            answers. For the simulator of this blueprint that is \
            http://localhost:8079/e-signature, and the profile 'local' of the application sets \
            it."""));
    final var callbackUrl = properties
        .callbackUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The e-signature service does not know where to report a signature. Set \
            'e-signature.callback-url' to the address of this application followed by '%s', as \
            the e-signature service reaches it. For a local run that is \
            http://localhost:8080%s."""
            .formatted(
                CALLBACK_PATH,
                CALLBACK_PATH)));

    // The generated client is an interface. The REST client of Quarkus implements it.
    final var client = QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .connectTimeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
        .readTimeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
        .build(EnvelopesApi.class);

    return new ContractSigningRestAdapter(client, baseUrl, callbackUrl);

  }

}
