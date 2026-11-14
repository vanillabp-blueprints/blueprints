package blueprint.workflowmodule.loanapproval.config;

import java.util.List;

import org.springframework.stereotype.Component;

import io.vanillabp.spi.cockpit.workflowmodules.WorkflowModuleDetailsProvider;

/**
 * Says who sees this workflow module in the Business Cockpit: the members of the group
 * {@value #LOAN_OFFICERS}, and nobody else.
 *
 * <p>
 * The cockpit adapter asks this bean when the module registers with the cockpit, and the
 * registration carries the groups. A cockpit user interface asks the cockpit which modules the
 * user may see, and loads the parts which belong to no case only from those modules. The button
 * "Request a loan" in the header is such a part. So a user outside the group never sees the
 * button, and the cockpit never loads the code of the start form for that user.
 * </p>
 *
 * <p>
 * The bean decides nothing about the task list and the case list. Whether a user sees a task or
 * a case is decided for each of them, from what the cockpit adapter reported about it. And it
 * does not protect the endpoint of the start form. A module which has to refuse a request of
 * somebody outside the group checks that in the endpoint as well.
 * </p>
 *
 * <p>
 * The module brings this bean itself, because the group is a decision of the module. Without
 * such a bean, every user of the cockpit sees the module. An application which names the group
 * differently, for each customer for example, takes the group from its configuration here.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/business-cockpit/wiki/Security#who-sees-which-workflow-module">Who
 *      sees which workflow module</a>
 */
@Component
public class LoanApprovalModuleDetails implements WorkflowModuleDetailsProvider {

  /** The group whose members may start a loan approval in the cockpit. */
  public static final String LOAN_OFFICERS = "LOAN_OFFICERS";

  @Override
  public String getWorkflowModuleId() {

    return LoanApprovalAutoConfiguration.WORKFLOW_MODULE_ID;

  }

  @Override
  public List<String> getAccessibleToGroups() {

    return List.of(LOAN_OFFICERS);

  }

}
