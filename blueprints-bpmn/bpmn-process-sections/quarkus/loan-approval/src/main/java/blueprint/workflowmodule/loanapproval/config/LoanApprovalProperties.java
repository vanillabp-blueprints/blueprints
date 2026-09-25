package blueprint.workflowmodule.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


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
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /** The highest credit rating the rating step may award. */
  @WithDefault("100")
  int ratingScale();

  /** From this rating on a loan may be approved. */
  @WithDefault("30")
  int minimumRating();

  /** From this amount on the risk people have to see the loan, which is the second section. */
  @WithDefault("10000")
  int riskAssessmentFrom();

  /** How many documents a complete application consists of. */
  @WithDefault("3")
  int documentsExpected();

  /** How much of the requested amount the customer's securities are assumed to cover. */
  @WithDefault("60")
  int collateralPercentage();

  /** No security counts for more than this, however big the loan is. */
  @WithDefault("12000")
  int maximumCollateral();

  /** This share of the amount has to be covered by a security. */
  @WithDefault("50")
  int minimumCollateralPercentage();

  /** Above this share of the income already spent on debt a loan is rejected. */
  @WithDefault("40")
  int maximumDebtRatio();

}
