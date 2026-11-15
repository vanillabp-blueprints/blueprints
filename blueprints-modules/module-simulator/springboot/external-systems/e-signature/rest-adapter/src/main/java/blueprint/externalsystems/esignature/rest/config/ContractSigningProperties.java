package blueprint.externalsystems.esignature.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the e-signature service reaches this application, read from
 * {@code e-signature.callback-url}. The address of the e-signature service itself and the other
 * client keys below {@code e-signature.*} belong to the client module of the API.
 *
 * @param callbackUrl The address of this application's callback endpoint, as the e-signature
 *                    service reaches it. Behind a proxy that is not the address the application
 *                    sees itself, so it is configured, not guessed.
 */
@ConfigurationProperties(prefix = "e-signature")
public record ContractSigningProperties(
                                        String callbackUrl) {
}
