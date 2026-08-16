package blueprint.workflowmodule.loanapproval.config;

import java.util.List;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
 *
 * <p>
 * The two lists decide how often the model loops, on both levels. Keeping them here means
 * a region more is a configuration change rather than a deployment.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /**
   * The highest credit rating the rating step may award.
   *
   * @return The rating scale.
   */
  @WithDefault("100")
  int ratingScale();

  /**
   * The regions assessed, one iteration of the subprocess each.
   *
   * @return The regions.
   */
  List<Region> regions();

  /**
   * The partners asked in every region, one iteration of the inner task each.
   *
   * @return The partners.
   */
  List<Partner> partners();

  /** A region the loan may be booked in. */
  interface Region {

    /**
     * How the region is addressed. This is the element an iteration is handed.
     *
     * @return The region's id.
     */
    String id();

    /**
     * What booking in this region adds to the rate, in basis points.
     *
     * @return The surcharge.
     */
    int surcharge();

  }

  /** A partner bank the loan is offered to. */
  interface Partner {

    /**
     * How the partner is addressed.
     *
     * @return The partner's id.
     */
    String id();

    /**
     * What this partner adds to the rate, in basis points.
     *
     * @return The spread.
     */
    int spread();

  }

}
