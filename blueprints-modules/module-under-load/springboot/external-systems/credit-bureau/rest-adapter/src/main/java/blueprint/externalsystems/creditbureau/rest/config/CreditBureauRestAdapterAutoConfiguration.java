package blueprint.externalsystems.creditbureau.rest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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
 * The client comes from {@code credit-bureau-api-client}, which builds it from the keys below
 * {@code credit-bureau.client}. A missing address stops the start with a message which names the
 * key.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@Import(CallbackController.class)
public class CreditBureauRestAdapterAutoConfiguration {

  /**
   * The credit bureau, over REST. Created while the application starts, so a missing callback
   * address stops the start with a message instead of failing the first loan approval.
   *
   * @param client      The generated client of the credit bureau.
   * @param callbackUrl The value of {@code credit-bureau.callback-url}: the address of this
   *                    application's callback endpoint, as the credit bureau reaches it. Behind a
   *                    proxy that is not the address the application sees itself, so it is
   *                    configured, not guessed.
   * @return The adapter.
   */
  @Bean
  public CreditBureau creditBureau(
      final ScoreRequestsApi client,
      @Value("${credit-bureau.callback-url:}") final String callbackUrl) {

    if (callbackUrl.isBlank()) {
      throw new IllegalStateException("""
          The credit bureau does not know where to send a score. Set \
          'credit-bureau.callback-url' to the address of this application followed by '%s', \
          as the credit bureau reaches it. For a local run that is http://localhost:8080%s."""
          .formatted(
              ScoreCallbackApi.PATH_REPORT_SCORE,
              ScoreCallbackApi.PATH_REPORT_SCORE));
    }

    return new CreditBureauRestAdapter(client, callbackUrl);

  }

}
