package blueprint.workflowmodule.loanapproval.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * The keys this workflow module reads itself. They are below the id of the module. They are not
 * below {@code vanillabp}, because VanillaBP and the cockpit adapter own that namespace and refuse
 * keys they do not read.
 *
 * <p>
 * The module wires itself without a class of its own. Quarkus finds its beans, its entity and its
 * repository through the Jandex index of the JAR, and every bean is known by its type. So two
 * modules with a class {@code Service} each live in one application side by side.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = LoanApprovalProperties.WORKFLOW_MODULE_ID)
public interface LoanApprovalProperties {

  /**
   * The id of this workflow module, as in {@code META-INF/workflow-module}. It is the first part
   * of the paths of the module and of the keys the module reads itself.
   */
  String WORKFLOW_MODULE_ID = "loan-approval";

  /**
   * The address of the Business Cockpit, for example {@code http://localhost:8080}. Without it,
   * the module does not ask the cockpit who took over a user task.
   *
   * @return The value of {@code loan-approval.cockpit.address}, if the application sets it.
   */
  @WithName("cockpit.address")
  Optional<String> cockpitAddress();

}
