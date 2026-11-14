package blueprint.externalsystems.esignature.rest.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the e-signature service is, and where it reaches this application, read from
 * {@code e-signature.*}. The adapter brings the keys, the application sets the values per
 * profile.
 *
 * @param baseUrl     The address of the e-signature service. There is no default, because no
 *                    address is right for every environment.
 * @param callbackUrl The address of this application's callback endpoint, as the e-signature
 *                    service reaches it. Behind a proxy that is not the address the application
 *                    sees itself, so it is configured, not guessed.
 * @param timeout     How long connecting and answering may take each.
 */
@ConfigurationProperties(prefix = "e-signature")
public record ContractSigningProperties(
                                        String baseUrl,
                                        String callbackUrl,
                                        @DefaultValue("5s") Duration timeout) {
}
