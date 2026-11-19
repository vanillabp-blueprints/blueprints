package blueprint.workflowmodule.loanapproval.overdraft;

import java.util.UUID;

import blueprint.workflowmodule.loanapproval.overdraft.api.OverdraftApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the overdraft request. Two GET requests: start one, look at it.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link OverdraftApi}, the interface the
 * build generates from {@code src/main/resources/loan-approval/openapi/overdraft.yaml}. This
 * class only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements OverdraftApi {

  @Inject
  Service overdraftRequest;

  /**
   * Starts an overdraft request.
   *
   * @param limit The overdraft limit asked for.
   * @return The id of the overdraft request started.
   */
  @Override
  public String start(
      final Integer limit) {

    final var overdraftId = UUID.randomUUID().toString();

    overdraftRequest.request(overdraftId, limit);

    log.info(
        "Show the overdraft request -> http://localhost:8080/loan-approval/api/overdrafts/{}",
        overdraftId);

    return overdraftId;

  }

  /**
   * Shows what the process did.
   *
   * @param overdraftId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String overdraftId) {

    return overdraftRequest
        .get(overdraftId)
        .map(Object::toString)
        .orElse("unknown overdraft request '"
            + overdraftId
            + "'");

  }

}
