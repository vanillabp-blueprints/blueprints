package blueprint.workflowmodule.credithistory;

import java.util.UUID;

import blueprint.workflowmodule.credithistory.api.CreditHistoryApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the credit history, GET requests only, so the process can be walked through in a
 * browser.
 *
 * <p>
 * The paths, the parameters and the answers come from {@link CreditHistoryApi}, the interface
 * the build generates from {@code src/main/resources/openapi/credit-history.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements CreditHistoryApi {

  @Inject
  Service creditHistory;

  /**
   * Requests a credit history.
   *
   * @param years How many years back to look.
   * @return The id of the request started.
   */
  @Override
  public String start(
      final Integer years) {

    final var creditHistoryId = UUID.randomUUID().toString();

    creditHistory.request(creditHistoryId, years);

    log.info(
        "Show the result -> http://localhost:8080/api/credit-history/{}",
        creditHistoryId);

    return creditHistoryId;

  }

  /**
   * Shows what the process did.
   *
   * @param creditHistoryId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String creditHistoryId) {

    return creditHistory
        .get(creditHistoryId)
        .map(Object::toString)
        .orElse("unknown credit history '"
            + creditHistoryId
            + "'");

  }

}
