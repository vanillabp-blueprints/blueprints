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
 * The partners live here rather than in the model: how many iterations a multi-instance
 * task runs is a question about the business case, and answering it in configuration keeps
 * the BPMN out of a deployment whenever a partner is added.
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

  /** The partners asked for an offer, one iteration of the multi-instance task each. */
  private List<Partner> partners = new ArrayList<>();

  /** A partner bank the loan is offered to. */
  @Data
  public static class Partner {

    /** How the partner is addressed. This is the element an iteration is handed. */
    private String id;

    /** What this partner adds to the rate, in basis points. */
    private int spread;

  }

}
