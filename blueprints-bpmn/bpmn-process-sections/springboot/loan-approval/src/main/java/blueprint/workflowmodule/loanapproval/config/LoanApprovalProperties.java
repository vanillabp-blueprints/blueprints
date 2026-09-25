package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * <p>
 * One configuration for both sections of the process. A section is a part of a model, not a
 * unit configuration belongs to.
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

  /** From this amount on the risk people have to see the loan, which is the second section. */
  private int riskAssessmentFrom = 10000;

  /** How many documents a complete application consists of. */
  private int documentsExpected = 3;

  /** How much of the requested amount the customer's securities are assumed to cover. */
  private int collateralPercentage = 60;

  /** No security counts for more than this, however big the loan is. */
  private int maximumCollateral = 12000;

  /** This share of the amount has to be covered by a security. */
  private int minimumCollateralPercentage = 50;

  /** Above this share of the income already spent on debt a loan is rejected. */
  private int maximumDebtRatio = 40;

}
