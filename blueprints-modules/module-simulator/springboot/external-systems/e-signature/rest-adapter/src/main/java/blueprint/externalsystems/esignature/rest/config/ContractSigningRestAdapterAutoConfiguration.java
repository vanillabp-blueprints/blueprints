package blueprint.externalsystems.esignature.rest.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

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
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ContractSigningProperties.class)
@Import(CallbackController.class)
public class ContractSigningRestAdapterAutoConfiguration {

  /**
   * The e-signature service, over REST. Created while the application starts, so a missing
   * address stops the start with a message instead of failing the first contract.
   *
   * @param restClients The REST client builder of Spring Boot, with its JSON converters.
   * @param properties  Where the e-signature service is, and where it calls back.
   * @return The adapter.
   */
  @Bean
  public ContractSigning contractSigning(
      final RestClient.Builder restClients,
      final ContractSigningProperties properties) {

    if ((properties.baseUrl() == null) || properties.baseUrl().isBlank()) {
      throw new IllegalStateException("""
          The e-signature service has no address. Set 'e-signature.base-url' to where it \
          answers. For the simulator of this blueprint that is \
          http://localhost:8079/e-signature, and the profile 'local' of the application sets \
          it.""");
    }
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
        .createClient(EnvelopesApi.class);

    return new ContractSigningRestAdapter(client, properties.baseUrl(), properties.callbackUrl());

  }

}
