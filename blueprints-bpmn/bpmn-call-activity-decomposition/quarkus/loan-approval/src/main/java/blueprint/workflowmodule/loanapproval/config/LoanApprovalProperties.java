package blueprint.workflowmodule.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


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

  /** How much of the requested amount the customer's securities are assumed to cover. */
  @WithDefault("60")
  int collateralPercentage();

  /** Above this share of the income already spent on debt a loan is rejected. */
  @WithDefault("40")
  int maximumDebtRatio();

}
