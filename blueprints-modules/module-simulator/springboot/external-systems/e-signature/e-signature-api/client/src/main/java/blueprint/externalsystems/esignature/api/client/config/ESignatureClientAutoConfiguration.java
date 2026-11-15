package blueprint.externalsystems.esignature.api.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import feign.Feign;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code e-signature.base-url} and injects {@link EnvelopesApi}. It writes no client
 * configuration of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into an
 * OpenFeign client: timeouts, TLS, proxy, logging, retries, JSON and authentication. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ESignatureClientProperties.class)
public class ESignatureClientAutoConfiguration extends ClientsConfigurationBase {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public ESignatureClientAutoConfiguration() {
  }

  /**
   * The client of the e-signature service. Created while the application starts, so a missing
   * address stops the start with a message instead of failing the first contract.
   *
   * @param properties Where the e-signature service is.
   * @return The client.
   */
  @Bean
  public EnvelopesApi envelopesApi(
      final ESignatureClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The e-signature service has no address. Set 'e-signature.base-url' to where it \
          answers. For the simulator of this blueprint that is \
          http://localhost:8079/e-signature, and the profile 'local' of the application sets \
          it.""");
    }

    final var builder = Feign.builder();
    configureFeignBuilder(EnvelopesApi.class, builder, properties);
    return builder.target(EnvelopesApi.class, properties.getBaseUrl());

  }

}
