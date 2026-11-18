package blueprint.externalsystems.creditrating.api.client;

/**
 * The client of the credit rating, as this module imports it. A type of its own, so an application
 * which imports {@link CreditRatingApi} into an HTTP service group of its own, for example with other
 * keys for a second workflow module, gets no second bean of this type.
 */
public interface CreditRatingClient extends CreditRatingApi {
}
