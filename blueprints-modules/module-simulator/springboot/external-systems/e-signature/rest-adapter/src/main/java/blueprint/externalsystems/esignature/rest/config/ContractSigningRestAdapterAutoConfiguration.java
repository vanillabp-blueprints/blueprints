package blueprint.externalsystems.esignature.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import blueprint.externalsystems.esignature.ContractSigning;
import blueprint.externalsystems.esignature.api.client.EnvelopesApi;
import blueprint.externalsystems.esignature.api.client.config.ESignatureClientAutoConfiguration;
import blueprint.externalsystems.esignature.api.client.config.ESignatureClientProperties;
import blueprint.externalsystems.esignature.callback.server.SignatureCallbackApi;
import blueprint.externalsystems.esignature.rest.CallbackController;
import blueprint.externalsystems.esignature.rest.ContractSigningRestAdapter;

/**
 * The adapter wires itself, like a workflow module does. An application which has this JAR on
 * its classpath gets a {@link ContractSigning} bean and the callback endpoint, and does nothing
 * else.
 *
 * <p>
 * The client comes from the client module of the API, which builds it out of the properties
 * {@code e-signature.*}. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration(after = ESignatureClientAutoConfiguration.class)
@EnableConfigurationProperties(ContractSigningProperties.class)
@Import(CallbackController.class)
public class ContractSigningRestAdapterAutoConfiguration {

  /**
   * Creates the configuration. Spring creates it, nobody else does.
   */
  public ContractSigningRestAdapterAutoConfiguration() {
  }

  /**
   * The e-signature service, over REST. Created while the application starts, so a missing
   * callback address stops the start with a message instead of failing the first contract.
   *
   * @param client           The generated client, built by the client module.
   * @param clientProperties Where the e-signature service is, for the messages of the adapter.
   * @param properties       Where the e-signature service calls back.
   * @return The adapter.
   */
  @Bean
  public ContractSigning contractSigning(
      final EnvelopesApi client,
      final ESignatureClientProperties clientProperties,
      final ContractSigningProperties properties) {

    if ((properties.callbackUrl() == null) || properties.callbackUrl().isBlank()) {
      throw new IllegalStateException("""
          The e-signature service does not know where to report a signature. Set \
          'e-signature.callback-url' to the address of this application followed by '%s', as \
          the e-signature service reaches it. For a local run that is \
          http://localhost:8080%s."""
          .formatted(
              SignatureCallbackApi.PATH_REPORT_SIGNATURE,
              SignatureCallbackApi.PATH_REPORT_SIGNATURE));
    }

    return new ContractSigningRestAdapter(client, clientProperties.getBaseUrl(), properties.callbackUrl());

  }

}
