package blueprint.workflowmodule.loanapproval.audit;

import org.hibernate.envers.RevisionListener;

/**
 * Who is changing the loan approval right now.
 *
 * <p>
 * Envers builds an {@link AuditedChange} itself, without asking the bean container, so
 * the name of the person cannot be injected into this listener. It is handed over on the
 * thread instead. In an application with a security framework this class reads the
 * authenticated user instead, and nothing else about the auditing changes.
 * </p>
 *
 * <p>
 * <strong>The name has to be there until the transaction commits.</strong> Envers writes
 * the revision row when the changes are flushed, which is after the business method
 * returned, so a name taken back inside that method comes too late.
 * {@link #attributeTo(String, Runnable)} therefore wraps the call which opens the
 * transaction, which is why it is used in the API rather than in the business code.
 * </p>
 *
 * <p>
 * A change nobody announced comes from the process: the service tasks run on threads of
 * the BPMS, where there is no person to name.
 * </p>
 */
public class ChangeAuthor implements RevisionListener {

  /** The author of every change which was not made by a person. */
  public static final String THE_PROCESS = "the process";

  private static final ThreadLocal<String> ACTING = new ThreadLocal<>();

  /**
   * Runs something and puts every change it makes into the trail under that name.
   *
   * @param user   The person acting.
   * @param change What they do, including the commit of the transaction it opens.
   */
  public static void attributeTo(
      final String user,
      final Runnable change) {

    ACTING.set(user);
    try {
      change.run();
    } finally {
      // The thread serves the next request afterwards, and a name left behind would end
      // up on somebody else's change.
      ACTING.remove();
    }

  }

  @Override
  public void newRevision(
      final Object revision) {

    final var name = ACTING.get();
    ((AuditedChange) revision).setChangedBy(name == null ? THE_PROCESS : name);

  }

}
