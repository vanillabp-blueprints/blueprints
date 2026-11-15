package blueprint.externalsystems.creditrating.api.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.externalsystems.creditrating.api.client.CreditRatingApi;
import feign.Feign;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code credit-rating.base-url} and injects {@link CreditRatingApi}. It writes no client
 * configuration of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into an
 * OpenFeign client: timeouts, TLS, proxy, logging, retries, JSON and authentication. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CreditRatingClientProperties.class)
public class CreditRatingClientAutoConfiguration extends ClientsConfigurationBase {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public CreditRatingClientAutoConfiguration() {
  }

  /**
   * The client of the credit rating. Created while the application starts, so a missing address
   * stops the start with a message instead of failing the first loan request.
   *
   * @param properties Where the credit rating is.
   * @return The client.
   */
  @Bean
  public CreditRatingApi creditRatingApi(
      final CreditRatingClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The credit rating has no address. Set 'credit-rating.base-url' to where it answers. \
          For the simulator of this blueprint that is http://localhost:8079/credit-rating, and \
          the profile 'local' of the application sets it.""");
    }

    final var builder = Feign.builder();
    configureFeignBuilder(CreditRatingApi.class, builder, properties);
    return builder.target(CreditRatingApi.class, properties.getBaseUrl());

  }

}
