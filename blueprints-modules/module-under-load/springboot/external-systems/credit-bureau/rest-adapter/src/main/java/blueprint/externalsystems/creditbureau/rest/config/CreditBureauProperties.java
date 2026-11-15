package blueprint.externalsystems.creditbureau.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the credit bureau reaches this application, read from
 * {@code credit-bureau.callback-url}. The address of the credit bureau itself and the other
 * client keys below {@code credit-bureau.*} belong to the client module of the API.
 *
 * @param callbackUrl The address of this application's callback endpoint, as the credit bureau
 *                    reaches it. Behind a proxy that is not the address the application sees
 *                    itself, so it is configured, not guessed.
 */
@ConfigurationProperties(prefix = "credit-bureau")
public record CreditBureauProperties(
                                     String callbackUrl) {
}
