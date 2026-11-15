package blueprint.simulator.testapi.client.config;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.StatusApi;
import io.quarkus.arc.Unremovable;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code simulator-test-api.base-url} and injects the generated interface. It writes no client
 * configuration of its own. A client is created when somebody asks for it, so a test which does not
 * use it needs no address. Quarkus keeps the clients although nothing injects them, because a test
 * helper looks them up in the bean container.
 *
 * <p>
 * Quarkus finds this class through the index the build of this module writes.
 * </p>
 */
@ApplicationScoped
public class SimulatorTestApiClientProducer {

  /**
   * The client of {@link StatusApi}.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Produces
  @Singleton
  @Unremovable
  public StatusApi simulatorStatusApi(
      final SimulatorTestApiClientProperties properties) {

    return build(StatusApi.class, properties);

  }

  /**
   * The client of {@link CreditRatingTestApi}.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Produces
  @Singleton
  @Unremovable
  public CreditRatingTestApi creditRatingTestApi(
      final SimulatorTestApiClientProperties properties) {

    return build(CreditRatingTestApi.class, properties);

  }

  /**
   * The client of {@link ESignatureTestApi}.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Produces
  @Singleton
  @Unremovable
  public ESignatureTestApi eSignatureTestApi(
      final SimulatorTestApiClientProperties properties) {

    return build(ESignatureTestApi.class, properties);

  }

  private static <T> T build(
      final Class<T> api,
      final SimulatorTestApiClientProperties properties) {

    final var baseUrl = properties
        .baseUrl()
        .filter(address -> !address.isBlank())
        .orElseThrow(() -> new IllegalStateException("""
            The test API has no address. Set 'simulator-test-api.base-url' to where the \
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
