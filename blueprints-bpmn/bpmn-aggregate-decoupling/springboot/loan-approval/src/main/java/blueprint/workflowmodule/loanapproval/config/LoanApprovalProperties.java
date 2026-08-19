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

  /** The highest credit rating the rating step may award. */
  private int ratingScale = 100;

  /**
   * From this rating on a request is risk class LOW, which is the answer the model reads as
   * "may be approved without a review". The number lives here rather than in the model, so
   * moving it is a configuration change instead of a new process version.
   */
  private int lowRiskRating = 30;

  /** Below the low-risk rating, but from this one on a person still looks at the request. */
  private int mediumRiskRating = 10;

}
