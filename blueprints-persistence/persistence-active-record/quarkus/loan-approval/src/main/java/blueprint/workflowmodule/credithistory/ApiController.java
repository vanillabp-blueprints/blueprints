package blueprint.workflowmodule.credithistory;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the credit history, GET requests only, so the process can be walked through in a
 * browser.
 */
@Slf4j
@ApplicationScoped
@Path("/api/credit-history")
public class ApiController {

  @Inject
  Service service;

  /**
   * Requests a credit history.
   *
   * @param years How many years back to look.
   * @return The id of the request started.
   */
  @GET
  @Path("/start")
  public String start(
      @QueryParam("years")
      @DefaultValue("3") final int years) {

    final var creditHistoryId = UUID.randomUUID().toString();

    service.requestCreditHistory(creditHistoryId, years);

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
  @GET
  @Path("/{creditHistoryId}")
  public String show(
      @PathParam("creditHistoryId") final String creditHistoryId) {

    return service
        .getCreditHistory(creditHistoryId)
        .map(Object::toString)
        .orElse("unknown credit history '"
            + creditHistoryId
            + "'");

  }

}
