package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

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
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "loan-approval")
@Data
public class LoanApprovalProperties {

  /** A customer with a lower credit score is declined at once, and no workflow starts. */
  private int minimumCreditScore = 600;

}
