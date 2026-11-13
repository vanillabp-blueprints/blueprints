package blueprint.workflowmodule.loanapproval.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml}, a configuration file the workflow module brings
 * along itself. So everything the module needs stays inside the module.
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

  /**
   * The documents to check for every loan request. The workflow opens one user task "Check
   * document" per entry.
   */
  private List<String> requiredDocuments = new ArrayList<>();

}
