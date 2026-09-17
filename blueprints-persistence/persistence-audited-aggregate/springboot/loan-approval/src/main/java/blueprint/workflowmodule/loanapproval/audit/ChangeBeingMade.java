package blueprint.workflowmodule.loanapproval.audit;

import java.util.UUID;

import org.hibernate.envers.RevisionListener;

/**
 * What the auditing has to be told about the change this thread is making: who is making
 * it, and the id which names it.
 *
 * <p>
 * Envers builds an {@link AuditedChange} itself, without asking the bean container, so
 * neither of the two can be injected into this listener. Both travel on the thread, and
 * this class is where they are put and picked up again.
 * </p>
 *
 * <p>
 * The two live for different lengths of time. The person is bound around a call which
 * opens a transaction, {@link #attributeTo(String, Runnable)}, because Envers writes the
 * revision when that transaction commits and a name taken back earlier would come too
 * late. The id belongs to one transaction: it is made when somebody asks for it, written
 * into the revision of that transaction, and forgotten right there.
 * </p>
 *
 * <p>
 * A change nobody announced comes from the process: the service tasks run on threads of
 * the BPMS, where there is no person to name.
 * </p>
 */
public class ChangeBeingMade implements RevisionListener {

  /** The author of every change which was not made by a person. */
  public static final String THE_PROCESS = "the process";

  private static final ThreadLocal<String> AUTHOR = new ThreadLocal<>();

  private static final ThreadLocal<String> ID = new ThreadLocal<>();

  /**
   * Runs something and puts every change it makes into the trail under that name.
   *
   * @param user   The person acting.
   * @param change What they do, including the commit of the transaction it opens.
   */
  public static void attributeTo(
      final String user,
      final Runnable change) {

    AUTHOR.set(user);
    try {
      change.run();
    } finally {
      // The thread serves the next request afterwards, and a name left behind would end
      // up on somebody else's change.
      AUTHOR.remove();
    }

  }

  /**
   * The id of the change this thread is making, made up here and written into the
   * revision when the transaction commits.
   *
   * <p>
   * This is what lets somebody name a state before it exists. The revision itself gets
   * its number from the database while the transaction commits, which is too late for
   * anybody who has to write the name down inside the transaction. An id made up here is
   * there immediately, and the revision of this transaction is the one which ends up
   * carrying it.
   * </p>
   *
   * <p>
   * Asked twice in one transaction, it answers the same id, because the revision it ends
   * up on is the same one. A transaction which rolls back leaves its id behind on the
   * thread, which costs nothing: the next transaction of that thread writes that id into
   * its own revision, and nobody holds the id of a transaction which never happened.
   * </p>
   *
   * @return The id of the change being made.
   */
  static String id() {

    var id = ID.get();
    if (id == null) {
      id = UUID.randomUUID().toString();
      ID.set(id);
    }
    return id;

  }

  @Override
  public void newRevision(
      final Object revision) {

    final var author = AUTHOR.get();
    final var change = (AuditedChange) revision;
    change.setChangedBy(author == null ? THE_PROCESS : author);
    change.setChangeId(id());

    // One revision per transaction, so the id of this one is used up. The next
    // transaction of this thread makes its own.
    ID.remove();

  }

}
