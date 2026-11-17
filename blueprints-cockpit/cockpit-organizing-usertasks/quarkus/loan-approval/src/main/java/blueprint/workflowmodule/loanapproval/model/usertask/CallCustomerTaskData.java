package blueprint.workflowmodule.loanapproval.model.usertask;

import java.time.OffsetDateTime;

/**
 * What the user task "Call customer" holds: how often the advisor tried, when to try again,
 * and at the end what the customer said.
 *
 * <p>
 * {@link #callAgainAt()} is the follow-up date of the task. The advisor did not reach the
 * customer, so the task leaves the list of open tasks until then and comes back the next
 * morning.
 * </p>
 *
 * @param attempts    How often the advisor called without reaching the customer.
 * @param callAgainAt When to call again, empty until a call failed.
 * @param note        What the customer said, written when the task is completed.
 */
public record CallCustomerTaskData(
                                   int attempts,
                                   OffsetDateTime callAgainAt,
                                   String note) implements TaskData {

  /**
   * The task as it is when it opens: nobody has called yet.
   *
   * @return A task with no attempts.
   */
  public static CallCustomerTaskData empty() {

    return new CallCustomerTaskData(0, null, null);

  }

  /**
   * One more call which did not reach the customer.
   *
   * @param nextCall When to call again.
   * @return The data after that call.
   */
  public CallCustomerTaskData notReached(
      final OffsetDateTime nextCall) {

    return new CallCustomerTaskData(attempts + 1, nextCall, note);

  }

  /**
   * The call which reached the customer. There is nothing to follow up any more.
   *
   * @param whatTheCustomerSaid The note of the advisor.
   * @return The data of the completed task.
   */
  public CallCustomerTaskData reached(
      final String whatTheCustomerSaid) {

    return new CallCustomerTaskData(attempts, null, whatTheCustomerSaid);

  }

}
