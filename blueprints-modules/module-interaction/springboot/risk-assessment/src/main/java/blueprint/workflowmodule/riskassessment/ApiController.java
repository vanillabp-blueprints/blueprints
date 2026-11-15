package blueprint.workflowmodule.riskassessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.riskassessment.api.RiskAssessmentApi;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case, GET requests only, so its process can be looked at in a browser.
 *
 * <p>
 * It shows what the assessment did; there is no endpoint starting one. Assessments are asked
 * for by another module through {@link blueprint.api.RiskAssessments}, and adding a second
 * way in would invite exactly the shortcut this blueprint argues against.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link RiskAssessmentApi}, the interface
 * the build generates from {@code src/main/resources/openapi/risk-assessment.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@RestController
public class ApiController implements RiskAssessmentApi {

  @Autowired
  private Service riskAssessment;

  /**
   * Shows what the assessment worked out.
   *
   * @param caseId The id of the case, which is the id the caller chose.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String caseId) {

    return riskAssessment
        .get(caseId)
        .map(Object::toString)
        .orElse("unknown case '"
            + caseId
            + "'");

  }

}
