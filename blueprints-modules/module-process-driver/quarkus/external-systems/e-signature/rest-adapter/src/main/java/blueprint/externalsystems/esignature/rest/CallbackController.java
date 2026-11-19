package blueprint.externalsystems.esignature.rest;

import blueprint.externalsystems.esignature.SignedContracts;
import blueprint.externalsystems.esignature.callback.server.SignatureCallbackApi;
import blueprint.externalsystems.esignature.callback.server.model.Callback;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

/**
 * Where the e-signature service reports what the signer did: signed the contract, or declined to
 * sign it. It is the second half of the adapter: the service calls this endpoint, and the endpoint
 * hands the news to {@link SignedContracts}, which the application implements.
 *
 * <p>
 * The path, the method and the JSON come from {@link SignatureCallbackApi}, the interface
 * generated from {@code e-signature-callback.yaml}. This class only implements it. The endpoint
 * is a POST with a JSON body, because the e-signature service decides what its callback looks
 * like. The rule that a blueprint has GET requests only is about the API a person uses in a
 * browser, and nobody types a callback.
 * </p>
 */
@ApplicationScoped
public class CallbackController implements SignatureCallbackApi {

  private final SignedContracts signedContracts;

  /**
   * Creates the controller.
   *
   * @param signedContracts The application, as far as it wants to hear about signatures.
   */
  public CallbackController(
      final SignedContracts signedContracts) {

    this.signedContracts = signedContracts;

  }

  /**
   * Takes one report of the e-signature service.
   *
   * @param callback The report.
   * @return 204 if the application took it, 404 if it does not know the contract, 400 for a
   *         report without a status.
   */
  @Override
  public Response reportSignature(
      final Callback callback) {

    // A status the API does not know is refused while the JSON is read. One which is missing
    // arrives here as null.
    if (callback.getStatus() == null) {
      return Response
          .status(Response.Status.BAD_REQUEST)
          .build();
    }

    final var known = switch (callback.getStatus()) {
      case SIGNED -> signedContracts.signed(callback.getContractId());
      case DECLINED -> signedContracts.declined(callback.getContractId());
    };

    return known
        ? Response
            .noContent()
            .build()
        : Response
            .status(Response.Status.NOT_FOUND)
            .build();

  }

}
