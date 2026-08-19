package blueprint.workflowmodule.riskassessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
@RequestMapping("/api/risk-assessment")
public class ApiController {

  @Autowired
  private Service service;

  /**
   * Shows what the assessment worked out.
   *
   * @param caseId The id of the case, which is the id the caller chose.
   * @return The workflow aggregate as it is stored right now.
   */
  @GetMapping("/{caseId}")
  public String show(
      @PathVariable final String caseId) {

    return service
        .getAssessment(caseId)
        .map(Object::toString)
        .orElse("unknown case '"
            + caseId
            + "'");

  }

}
