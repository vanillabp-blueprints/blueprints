package blueprint.simulator.loadrun.api.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.simulator.loadrun.api.client.LoadRunApi;
import feign.Feign;
import feign.codec.StringDecoder;
import feign.jackson3.Jackson3Decoder;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code load-run-api.base-url} and injects {@link LoadRunApi}. It writes no client configuration
 * of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into an
 * OpenFeign client: timeouts, TLS, proxy, logging, retries, JSON and authentication. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LoadRunClientProperties.class)
public class LoadRunClientAutoConfiguration extends ClientsConfigurationBase {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public LoadRunClientAutoConfiguration() {
  }

  /**
   * The client of {@link LoadRunApi}.
   *
   * @param properties Where the simulator which drives the load run is.
   * @return The client.
   */
  @Bean
  public LoadRunApi loadRunApi(
      final LoadRunClientProperties properties) {

    return build(LoadRunApi.class, properties);

  }

  /**
   * Some answers of this API are plain text, not JSON. The JSON decoder of the support would fail
   * on them, so a {@code String} is read as text and everything else as JSON.
   */
  @Override
  protected void configureJsonEncoding(
      final Feign.Builder builder) {

    super.configureJsonEncoding(builder);
    final var json = new Jackson3Decoder(objectMapper.orElseGet(JsonMapper::new));
    final var text = new StringDecoder();
    builder.decoder((
        response,
        type) -> String.class.equals(type)
            ? text.decode(response, type)
            : json.decode(response, type));

  }

  private <T> T build(
      final Class<T> api,
      final LoadRunClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The client of the load run has no address. Set 'load-run-api.base-url' to where the \
          simulator answers, for example http://localhost:8079.""");
    }

    final var builder = Feign.builder();
    configureFeignBuilder(api, builder, properties);
    return builder.target(api, properties.getBaseUrl());

  }

}
