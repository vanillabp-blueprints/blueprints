package blueprint.workflowmodule.loanapproval.model.usertask;

/**
 * What the user task "Approve loan" holds once it is completed.
 *
 * @param approved Whether the loan is granted, empty while nobody has decided.
 * @param comment  Why, in the words of the user.
 */
public record ApproveLoanTaskData(
                                  Boolean approved,
                                  String comment) implements TaskData {

  /**
   * The form as it is when the task opens.
   *
   * @return A form with no values.
   */
  public static ApproveLoanTaskData empty() {

    return new ApproveLoanTaskData(null, null);

  }

}
