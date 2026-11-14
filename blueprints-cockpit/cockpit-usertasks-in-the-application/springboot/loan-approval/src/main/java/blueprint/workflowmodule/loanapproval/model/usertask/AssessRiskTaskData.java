package blueprint.workflowmodule.loanapproval.model.usertask;

/**
 * What the form of the user task "Assess risk" holds. While the task is open, this is a draft:
 * the values may be empty and may change with every save. Once the task is completed, it is
 * what the user sent at the end.
 *
 * @param riskAcceptable Whether the risk is acceptable, empty while nobody has decided.
 * @param comment        Why, in the words of the user.
 */
public record AssessRiskTaskData(
                                 Boolean riskAcceptable,
                                 String comment) implements TaskData {

  /**
   * The form as it is when the task opens.
   *
   * @return A form with no values.
   */
  public static AssessRiskTaskData empty() {

    return new AssessRiskTaskData(null, null);

  }

}
