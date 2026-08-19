package blueprint.workflowmodule.riskassessment;

import blueprint.api.WorkflowModuleBeanNameGenerator;

/**
 * Names this module's beans {@code risk-assessment_<SimpleName>}, the twin of the loan
 * approval's generator.
 */
public class RiskAssessmentBeanNames extends WorkflowModuleBeanNameGenerator {

  public RiskAssessmentBeanNames() {

    super("risk-assessment");

  }

}
