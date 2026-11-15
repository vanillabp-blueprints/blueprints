package blueprint.externalsystems.creditbureau.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
import blueprint.externalsystems.creditbureau.api.client.config.CreditBureauClientAutoConfiguration;
import blueprint.externalsystems.creditbureau.api.client.config.CreditBureauClientProperties;
import blueprint.externalsystems.creditbureau.callback.server.ScoreCallbackApi;
import blueprint.externalsystems.creditbureau.rest.CallbackController;
import blueprint.externalsystems.creditbureau.rest.CreditBureauRestAdapter;
import blueprint.workflowmodule.loanapproval.CreditBureau;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link CreditBureau} bean and the callback endpoint, and does nothing
 * else.
 *
 * <p>
 * The client comes from the client module of the API, which builds it out of the properties
 * {@code credit-bureau.*}. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration(after = CreditBureauClientAutoConfiguration.class)
@EnableConfigurationProperties(CreditBureauProperties.class)
@Import(CallbackController.class)
public class CreditBureauRestAdapterAutoConfiguration {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public CreditBureauRestAdapterAutoConfiguration() {
  }

  /**
   * The credit bureau, over REST. Created while the application starts, so a missing callback
   * address stops the start with a message instead of failing the first loan approval.
   *
   * @param client           The generated client, built by the client module.
   * @param clientProperties Where the credit bureau is, for the messages of the adapter.
   * @param properties       Where the credit bureau calls back.
   * @return The adapter.
   */
  @Bean
  public CreditBureau creditBureau(
      final ScoreRequestsApi client,
      final CreditBureauClientProperties clientProperties,
      final CreditBureauProperties properties) {

    if ((properties.callbackUrl() == null) || properties.callbackUrl().isBlank()) {
      throw new IllegalStateException("""
          The credit bureau does not know where to send a score. Set \
          'credit-bureau.callback-url' to the address of this application followed by '%s', \
          as the credit bureau reaches it. For a local run that is http://localhost:8080%s."""
          .formatted(
              ScoreCallbackApi.PATH_REPORT_SCORE,
              ScoreCallbackApi.PATH_REPORT_SCORE));
    }

    return new CreditBureauRestAdapter(client, clientProperties.getBaseUrl(), properties.callbackUrl());

  }

}
