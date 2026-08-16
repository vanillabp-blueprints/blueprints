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
 * The partners live here rather than in the model: how many iterations a multi-instance
 * task runs is a question about the business case, and answering it in configuration keeps
 * the BPMN out of a deployment whenever a partner is added.
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
   * The partners asked for an offer, one iteration of the multi-instance task each.
   *
   * @return The partners.
   */
  List<Partner> partners();

  /** A partner bank the loan is offered to. */
  interface Partner {

    /**
     * How the partner is addressed. This is the element an iteration is handed.
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
