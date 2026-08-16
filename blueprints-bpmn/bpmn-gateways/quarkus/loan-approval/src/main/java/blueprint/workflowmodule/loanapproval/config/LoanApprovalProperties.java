package blueprint.workflowmodule.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /** The highest credit rating the rating step may award. */
  @WithDefault("100")
  int ratingScale();

  /**
   * From this rating on a loan is approved without anybody looking at it. It is the
   * threshold the gateway routes on, and it lives here rather than in the BPMN: moving it
   * is a configuration change, not a new process version.
   */
  @WithDefault("30")
  int minimumRating();

  /** Below the minimum, but from this rating on a person still looks at the request. */
  @WithDefault("10")
  int manualReviewRating();

}
