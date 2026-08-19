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
   * From this rating on a request is risk class LOW, which is the answer the model reads as
   * "may be approved without a review". The number lives here rather than in the model, so
   * moving it is a configuration change instead of a new process version.
   */
  @WithDefault("30")
  int lowRiskRating();

  /** Below the low-risk rating, but from this one on a person still looks at the request. */
  @WithDefault("10")
  int mediumRiskRating();

}
