package blueprint.workflowmodule.loanapproval.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

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
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "loan-approval")
@Data
public class LoanApprovalProperties {

  /** The highest credit rating the rating step may award. */
  private int ratingScale = 100;

  /** The regions assessed, one iteration of the subprocess each. */
  private List<Region> regions = new ArrayList<>();

  /** The partners asked in every region, one iteration of the inner task each. */
  private List<Partner> partners = new ArrayList<>();

  /** A region the loan may be booked in. */
  @Data
  public static class Region {

    /** How the region is addressed. This is the element an iteration is handed. */
    private String id;

    /** What booking in this region adds to the rate, in basis points. */
    private int surcharge;

  }

  /** A partner bank the loan is offered to. */
  @Data
  public static class Partner {

    /** How the partner is addressed. */
    private String id;

    /** What this partner adds to the rate, in basis points. */
    private int spread;

  }

}
