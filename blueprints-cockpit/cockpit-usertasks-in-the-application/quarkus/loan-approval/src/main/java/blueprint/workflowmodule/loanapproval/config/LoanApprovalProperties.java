package blueprint.workflowmodule.loanapproval.config;

import java.util.List;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml}, a configuration file the workflow module brings
 * along itself. So everything the module needs stays inside the module.
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

  /**
   * The documents to check for every loan request. The workflow opens one user task "Check
   * document" per entry. The module's own configuration file names them, and Quarkus refuses
   * to start without the key.
   */
  List<String> requiredDocuments();

}
