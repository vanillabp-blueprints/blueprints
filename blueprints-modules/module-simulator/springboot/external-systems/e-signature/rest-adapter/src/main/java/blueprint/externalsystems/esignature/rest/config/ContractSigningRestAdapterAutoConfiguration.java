package blueprint.externalsystems.esignature.rest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import blueprint.externalsystems.esignature.callback.server.SignatureCallbackApi;
import blueprint.externalsystems.esignature.rest.CallbackController;
import blueprint.externalsystems.esignature.rest.ContractSigningRestAdapter;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link ContractSigning} bean and the callback endpoint, and does nothing
 * else.
 *
 * <p>
 * The client comes from {@code e-signature-api-client}, which builds it from the keys below
 * {@code e-signature.client}. A missing address stops the start with a message which names the
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
public class ContractSigningRestAdapterAutoConfiguration {

  /**
   * The e-signature service, over REST. Created while the application starts, so a missing
   * callback address stops the start with a message instead of failing the first contract.
   *
   * @param client      The generated client of the e-signature service.
   * @param callbackUrl The value of {@code e-signature.callback-url}: the address of this
   *                    application's callback endpoint, as the e-signature service reaches it.
   *                    Behind a proxy that is not the address the application sees itself, so it
   *                    is configured, not guessed.
   * @return The adapter.
   */
  @Bean
  public ContractSigning contractSigning(
      final EnvelopesApi client,
      @Value("${e-signature.callback-url:}") final String callbackUrl) {

    if (callbackUrl.isBlank()) {
      throw new IllegalStateException("""
          The e-signature service does not know where to report a signature. Set \
          'e-signature.callback-url' to the address of this application followed by '%s', as \
          the e-signature service reaches it. For a local run that is \
          http://localhost:8080%s."""
          .formatted(
              SignatureCallbackApi.PATH_REPORT_SIGNATURE,
              SignatureCallbackApi.PATH_REPORT_SIGNATURE));
    }

    return new ContractSigningRestAdapter(client, callbackUrl);

  }

}
