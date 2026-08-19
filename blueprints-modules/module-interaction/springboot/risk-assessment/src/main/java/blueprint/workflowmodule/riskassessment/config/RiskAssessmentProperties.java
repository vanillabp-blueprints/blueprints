package blueprint.workflowmodule.riskassessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module, fed from {@code risk-assessment/risk-assessment.yaml}
 * inside this JAR. Each module brings its own file, named after its own module id, so two
 * modules cannot overwrite each other's values.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "risk-assessment")
@Data
public class RiskAssessmentProperties {

  /** The highest score this assessment awards. */
  private int scoreLimit = 100;

}
