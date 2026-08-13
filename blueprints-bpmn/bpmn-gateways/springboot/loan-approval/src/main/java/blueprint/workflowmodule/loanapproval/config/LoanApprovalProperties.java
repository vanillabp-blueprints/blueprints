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
   * From this rating on a loan is approved without anybody looking at it. It is the
   * threshold the gateway routes on, and it lives here rather than in the BPMN: moving it
   * is a configuration change, not a new process version.
   */
  private int minimumRating = 30;

  /** Below the minimum, but from this rating on a person still looks at the request. */
  private int manualReviewRating = 10;

}
