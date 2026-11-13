package blueprint.externalsystems.creditrating.rest.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the credit rating is, read from {@code credit-rating.*}. The adapter brings the keys, the
 * application sets the values: the address of the simulator in the profile {@code local}, the
 * address of the real system in the profile {@code real}.
 *
 * @param baseUrl The address of the credit rating. There is no default, because no address is
 *                right for every environment.
 * @param timeout How long connecting and answering may take each. The user waits for this
 *                answer, so it is short.
 */
@ConfigurationProperties(prefix = "credit-rating")
public record CreditRatingProperties(
                                     String baseUrl,
                                     @DefaultValue("2s") Duration timeout) {
}
