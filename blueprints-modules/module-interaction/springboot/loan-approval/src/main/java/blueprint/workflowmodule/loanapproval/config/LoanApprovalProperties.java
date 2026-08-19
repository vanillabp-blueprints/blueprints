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
   * Which rating provider the module talks to. A value that really differs per environment,
   * which is why it lives in the module's profile files rather than in the application's.
   */
  private String ratingProvider = "internal";

}
