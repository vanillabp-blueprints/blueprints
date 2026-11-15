package blueprint.workflowmodule.loanapproval.api.client.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import blueprint.workflowmodule.loanapproval.api.client.LoanApprovalApi;
import feign.Feign;
import feign.codec.StringDecoder;
import feign.jackson3.Jackson3Decoder;
import io.vanillabp.cockpit.support.rest.adapter.ClientsConfigurationBase;
import okhttp3.ConnectionPool;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client module wires its client. An application which has this JAR on its classpath sets
 * {@code loan-approval-api.base-url} and injects {@link LoanApprovalApi}. It writes no client
 * configuration of its own.
 *
 * <p>
 * {@link ClientsConfigurationBase} of the Business Cockpit support turns the properties into an
 * OpenFeign client: timeouts, TLS, proxy, logging, retries, JSON and authentication. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LoanApprovalClientProperties.class)
public class LoanApprovalClientAutoConfiguration extends ClientsConfigurationBase {

  /** How many open connections are kept between two calls, more than the load driver uses. */
  private static final int IDLE_CONNECTIONS = 64;

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public LoanApprovalClientAutoConfiguration() {
  }

  /**
   * The client of {@link LoanApprovalApi}.
   *
   * @param properties Where the application under load is.
   * @return The client.
   */
  @Bean
  public LoanApprovalApi loanApprovalApi(
      final LoanApprovalClientProperties properties) {

    return build(LoanApprovalApi.class, properties);

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
      final LoanApprovalClientProperties properties) {

    if (!properties.isInitialized()) {
      throw new IllegalStateException("""
          The client of the loan approval has no address. Set 'loan-approval-api.base-url' to \
          where the API of the application answers, for example http://localhost:8080.""");
    }

    final var builder = Feign.builder();
    // Many customers and clerks call at the same time. Their connections stay open between two
    // calls, as the front ends of many customers would keep them. OkHttp keeps five by default.
    configureFeignBuilder(api, builder, properties, httpClient -> httpClient
        .connectionPool(new ConnectionPool(IDLE_CONNECTIONS, 5, TimeUnit.MINUTES)));
    return builder.target(api, properties.getBaseUrl());

  }

}
