package blueprint.externalsystems.creditbureau.api.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import feign.Feign;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code credit-bureau.base-url} and injects {@link ScoreRequestsApi}. It writes no client
 * configuration of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into an
 * OpenFeign client: timeouts, TLS, proxy, logging, retries, JSON and authentication. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CreditBureauClientProperties.class)
public class CreditBureauClientAutoConfiguration extends ClientsConfigurationBase {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public CreditBureauClientAutoConfiguration() {
  }

  /**
   * The client of {@link ScoreRequestsApi}.
   *
   * @param properties Where the credit bureau is.
   * @return The client.
   */
  @Bean
  public ScoreRequestsApi scoreRequestsApi(
      final CreditBureauClientProperties properties) {

    return build(ScoreRequestsApi.class, properties);

  }

  private <T> T build(
      final Class<T> api,
      final CreditBureauClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The credit bureau has no address. Set 'credit-bureau.base-url' to where it answers. \
          For the simulator of this blueprint that is http://localhost:8079/credit-bureau, and \
          'application.yaml' sets it.""");
    }

    final var builder = Feign.builder();
    configureFeignBuilder(api, builder, properties);
    return builder.target(api, properties.getBaseUrl());

  }

}
