package blueprint.simulator.loadrun.api.client.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.simulator.loadrun.api.client.LoadRunApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code load-run-api.base-url} and injects the generated interface. It writes no client
 * configuration of its own. Created when somebody injects it, so an application which does not use
 * it needs no address.
 *
 * <p>
 * Quarkus finds this class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class LoadRunClientProducer {

  /**
   * The client of {@link LoadRunApi}.
   *
   * @param properties Where the simulator which drives the load run is.
   * @return The client.
   */
  @Produces
  @Singleton
  public LoadRunApi loadRunApi(
      final LoadRunClientProperties properties) {

    return build(LoadRunApi.class, properties);

  }

  private static <T> T build(
      final Class<T> api,
      final LoadRunClientProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The client of the load run has no address. Set 'load-run-api.base-url' to where the \
            simulator answers, for example http://localhost:8079."""));

    // The generated client is an interface. The REST client of Quarkus implements it.
    return QuarkusRestClientBuilder
        .newBuilder()
        .baseUri(URI.create(baseUrl))
        .connectTimeout(properties.connectTimeout(), TimeUnit.MILLISECONDS)
        .readTimeout(properties.readTimeout(), TimeUnit.MILLISECONDS)
        .build(api);

  }

}
