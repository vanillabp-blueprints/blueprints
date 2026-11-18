package blueprint.externalsystems.esignature.api.client;

/**
 * The client of the e-signature service, as this module imports it. A type of its own, so an application
 * which imports {@link EnvelopesApi} into an HTTP service group of its own, for example with other
 * keys for a second workflow module, gets no second bean of this type.
 */
public interface ESignatureClient extends EnvelopesApi {
}
