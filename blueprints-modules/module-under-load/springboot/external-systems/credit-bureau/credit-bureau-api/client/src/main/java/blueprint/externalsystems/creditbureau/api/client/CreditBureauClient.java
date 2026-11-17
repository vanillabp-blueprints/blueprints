package blueprint.externalsystems.creditbureau.api.client;

/**
 * The client of the credit bureau, as this module imports it. A type of its own, so an application
 * which imports {@link ScoreRequestsApi} into an HTTP service group of its own, for example with other
 * keys for a second workflow module, gets no second bean of this type.
 */
public interface CreditBureauClient extends ScoreRequestsApi {
}
