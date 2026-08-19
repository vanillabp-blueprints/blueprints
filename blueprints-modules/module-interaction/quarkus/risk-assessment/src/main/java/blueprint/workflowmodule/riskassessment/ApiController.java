package blueprint.workflowmodule.riskassessment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case, GET requests only, so its process can be looked at in a browser.
 *
 * <p>
 * It shows what the assessment did; there is no endpoint starting one. Assessments are asked
 * for by another module through {@link blueprint.api.RiskAssessments}, and adding a second
 * way in would invite exactly the shortcut this blueprint argues against.
 * </p>
 */
@Slf4j
@ApplicationScoped
@Path("/api/risk-assessment")
public class ApiController {

  @Inject
  Service service;

  /**
   * Shows what the assessment worked out.
   *
   * @param caseId The id of the case, which is the id the caller chose.
   * @return The workflow aggregate as it is stored right now.
   */
  @GET
  @Path("/{caseId}")
  public String show(
      @PathParam("caseId") final String caseId) {

    return service
        .getAssessment(caseId)
        .map(Object::toString)
        .orElse("unknown case '"
            + caseId
            + "'");

  }

}
