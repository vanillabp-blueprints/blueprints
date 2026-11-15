package blueprint.workflowmodule.loanapproval.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml}, a file the workflow module brings along itself.
 *
 * <p>
 * Where the systems around the module are is not configured here. The module does not know
 * them, it knows their ports. The addresses belong to the adapters, and the application sets
 * them.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /**
   * A customer with a lower credit score is declined at once, and no workflow starts.
   *
   * @return The lowest credit score which starts a workflow.
   */
  @WithDefault("600")
  int minimumCreditScore();

}
