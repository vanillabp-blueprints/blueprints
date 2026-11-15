package blueprint.externalsystems.creditbureau.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import blueprint.externalsystems.creditbureau.api.client.ScoreRequestsApi;
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
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CreditBureauProperties.class)
@Import(CallbackController.class)
public class CreditBureauRestAdapterAutoConfiguration {

  /**
   * The credit bureau, over REST. Created while the application starts, so a missing address
   * stops the start with a message instead of failing the first loan approval.
   *
   * @param restClients The REST client builder of Spring Boot, with its JSON converters.
   * @param properties  Where the credit bureau is, and where it calls back.
   * @return The adapter.
   */
  @Bean
  public CreditBureau creditBureau(
      final RestClient.Builder restClients,
      final CreditBureauProperties properties) {

    if ((properties.baseUrl() == null) || properties.baseUrl().isBlank()) {
      throw new IllegalStateException("""
          The credit bureau has no address. Set 'credit-bureau.base-url' to where it answers. \
          For the simulator of this blueprint that is http://localhost:8079/credit-bureau, and \
          'application.yaml' sets it.""");
    }
    if ((properties.callbackUrl() == null) || properties.callbackUrl().isBlank()) {
      throw new IllegalStateException("""
          The credit bureau does not know where to send a score. Set \
          'credit-bureau.callback-url' to the address of this application followed by '%s', \
          as the credit bureau reaches it. For a local run that is http://localhost:8080%s."""
          .formatted(
              ScoreCallbackApi.PATH_REPORT_SCORE,
              ScoreCallbackApi.PATH_REPORT_SCORE));
    }

    final var timeouts = new SimpleClientHttpRequestFactory();
    timeouts.setConnectTimeout(properties.timeout());
    timeouts.setReadTimeout(properties.timeout());

    // The generated client is an interface. Spring implements it with a RestClient.
    final var restClient = restClients
        .baseUrl(properties.baseUrl())
        .requestFactory(timeouts)
        .build();
    final var client = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(ScoreRequestsApi.class);

    return new CreditBureauRestAdapter(client, properties.baseUrl(), properties.callbackUrl());

  }

}
