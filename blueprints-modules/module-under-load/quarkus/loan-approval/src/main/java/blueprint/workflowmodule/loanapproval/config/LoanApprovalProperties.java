package blueprint.workflowmodule.loanapproval.config;

import java.time.Duration;

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
   * How often the bank pays out the approved loans. A loan approved now waits for the next
   * run, together with every other loan approved before it.
   *
   * @return The time between two payout runs.
   */
  @WithDefault("PT20S")
  Duration payoutRunEvery();

}
