package blueprint.workflowmodule.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
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
   * The file H2 writes the statements it executes into, counted by the endpoint which shows
   * what this application costs while it waits.
   *
   * @return The path of the trace file.
   */
  @WithDefault("target/loan-approval.trace.db")
  String databaseTrace();

}
