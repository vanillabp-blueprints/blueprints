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

  /** The highest credit rating the rating provider may award. */
  @WithDefault("100")
  int ratingScale();

  /** Below this rating a loan is rejected, which the process learns as a BPMN error. */
  @WithDefault("10")
  int minimumRating();

  /**
   * Whether the stand-in rating provider fails the first request per loan request. On by
   * default, because a retry nobody ever sees teaches nothing.
   */
  @WithDefault("true")
  boolean failFirstRatingAttempt();

}
