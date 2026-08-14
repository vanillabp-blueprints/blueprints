package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * <p>
 * One configuration for both processes: the called process is a section of the same
 * workflow module, and a module is the unit configuration belongs to.
 * </p>
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

  /** From this rating on a loan may be approved. */
  private int minimumRating = 30;

  /** How much of the requested amount the customer's securities are assumed to cover. */
  private int collateralPercentage = 60;

  /** Above this share of the income already spent on debt a loan is rejected. */
  private int maximumDebtRatio = 40;

}
