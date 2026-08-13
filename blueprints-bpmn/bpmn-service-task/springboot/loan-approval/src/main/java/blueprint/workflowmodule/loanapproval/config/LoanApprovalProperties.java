package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "loan-approval")
@Data
public class LoanApprovalProperties {

  /** The highest credit rating the rating provider may award. */
  private int ratingScale = 100;

  /** Below this rating a loan is rejected, which the process learns as a BPMN error. */
  private int minimumRating = 10;

  /**
   * Whether the stand-in rating provider fails the first request per loan request. On by
   * default, because a retry nobody ever sees teaches nothing.
   */
  private boolean failFirstRatingAttempt = true;

}
