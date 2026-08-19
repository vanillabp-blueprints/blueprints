package blueprint.workflowmodule.riskassessment.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module, fed from {@code risk-assessment/risk-assessment.yaml}
 * inside this JAR. Each module brings its own file, named after its own module id, so two
 * modules cannot overwrite each other's values.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "risk-assessment")
public interface RiskAssessmentProperties {

  /**
   * The highest score this assessment awards.
   *
   * @return The limit.
   */
  @WithDefault("100")
  int scoreLimit();

}
