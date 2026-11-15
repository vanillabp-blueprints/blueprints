package blueprint.simulator.testapi.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.simulator.testapi.client.CreditRatingTestApi;
import blueprint.simulator.testapi.client.ESignatureTestApi;
import blueprint.simulator.testapi.client.StatusApi;
import feign.Feign;
import feign.codec.StringDecoder;
import feign.jackson3.Jackson3Decoder;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client module wires its clients. A test which has this JAR on its classpath sets
 * {@code simulator-test-api.base-url} and injects {@link StatusApi}, {@link CreditRatingTestApi}
 * and {@link ESignatureTestApi}. It writes no client configuration of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into
 * OpenFeign clients. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(SimulatorTestApiClientProperties.class)
public class SimulatorTestApiClientAutoConfiguration extends ClientsConfigurationBase {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public SimulatorTestApiClientAutoConfiguration() {
  }

  /**
   * The status of the simulator.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Bean
  public StatusApi simulatorStatusApi(
      final SimulatorTestApiClientProperties properties) {

    return build(StatusApi.class, properties);

  }

  /**
   * The test API of the simulated credit rating.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Bean
  public CreditRatingTestApi creditRatingTestApi(
      final SimulatorTestApiClientProperties properties) {

    return build(CreditRatingTestApi.class, properties);

  }

  /**
   * The test API of the simulated e-signature service.
   *
   * @param properties Where the simulator is.
   * @return The client.
   */
  @Bean
  public ESignatureTestApi eSignatureTestApi(
      final SimulatorTestApiClientProperties properties) {

    return build(ESignatureTestApi.class, properties);

  }

  /**
   * Most answers of the test API are plain text, not JSON. The JSON decoder of the support would
   * fail on them, so a {@code String} is read as text and everything else as JSON.
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
      final SimulatorTestApiClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The test API has no address. Set 'simulator-test-api.base-url' to where the \
          simulator answers, for example http://localhost:8079.""");
    }

    final var builder = Feign.builder();
    configureFeignBuilder(api, builder, properties);
    return builder.target(api, properties.getBaseUrl());

  }

}
