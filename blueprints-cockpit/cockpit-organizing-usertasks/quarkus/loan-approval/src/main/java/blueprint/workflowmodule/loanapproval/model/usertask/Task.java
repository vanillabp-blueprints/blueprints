package blueprint.workflowmodule.loanapproval.model.usertask;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Basic;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One user task of a loan approval, from the moment the BPMS opens it until it is completed
 * or canceled. Every user task is a row of its own, so the same task definition may be open
 * several times at once, and every one of them keeps its own draft.
 *
 * <p>
 * The draft is in {@link #getData() data}, and only there. Saving a form changes this row and
 * nothing else. The workflow aggregate learns about the result when the task is completed, in
 * the same transaction which completes the task in the BPMS. Until then, a branch of the
 * workflow which runs at the same time cannot see a half-finished answer and take it for the
 * truth.
 * </p>
 *
 * <p>
 * The row stays after the task is done. It says who completed the task and when, or that the
 * workflow canceled it. A form opened later still finds what was entered.
 * </p>
 *
 * <p>
 * The row also keeps who worked on the task, in {@link #getEditors() editors}. The Business
 * Cockpit gets this list as the admitted users of the task, and {@code UserTaskAccess} lets the
 * same people read the form. So somebody who worked on a task can still read it after they
 * moved to another team. There is one list for both sides on purpose. Two lists would drift
 * apart, and the cockpit would show a task whose form then answers 403.
 * </p>
 */
@Entity
@Table(name = "LOAN_APPROVAL_TASK")
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

  /** The id the BPMS gave the user task. */
  @Id
  private String taskId;

  /**
   * The draft while the task is open, and what the user sent once it is completed.
   *
   * <p>
   * Loaded lazily, when somebody reads it. Reading the tasks of a loan approval, to find one
   * or to know which are open, does not load the forms of all of them. Only the form which is
   * read is loaded. JPA can do that for a single attribute only when the entity was rewritten
   * by the bytecode enhancement of Hibernate, which {@code loan-approval/pom.xml} switches on.
   * The JSON may be large, so this is worth the plugin.
   * </p>
   */
  @Basic(fetch = FetchType.LAZY)
  @Column(length = 4000)
  @Convert(converter = TaskDataConverter.class)
  @Getter(AccessLevel.NONE)
  private TaskData data;

  /** When the BPMS opened the task. */
  @Column
  private OffsetDateTime createdAt;

  /** When a draft was saved last, empty if never. */
  @Column
  private OffsetDateTime updatedAt;

  /** When the task was completed or canceled, empty while it is open. */
  @Column
  private OffsetDateTime completedAt;

  /** Who completed the task. Empty while it is open, and empty if the workflow canceled it. */
  @Column
  private String completedBy;

  /**
   * The users who worked on the task: everybody who opened its form to work on it, and the one
   * who completed it. A user is written once, in the order they came.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_TASK_EDITOR", joinColumns = @JoinColumn(name = "TASK_ID"))
  @OrderColumn(name = "POSITION")
  @Column(name = "USER_ID")
  private List<String> editors = new ArrayList<>();

  /**
   * A user task the BPMS just opened.
   *
   * @param taskId The id the BPMS gave the task.
   * @param data   What the form holds at the start.
   * @return The new task.
   */
  public static Task opened(
      final String taskId,
      final TaskData data) {

    final var task = new Task();
    task.taskId = taskId;
    task.data = data;
    task.createdAt = OffsetDateTime.now();
    return task;

  }

  /**
   * The data of the form, already of the type the caller expects. A convenience, because each
   * kind of user task knows which record it stores.
   *
   * @param <T> The record of this kind of user task.
   * @return The draft, or what was sent at the end.
   * @throws ClassCastException If the task holds data of another kind of user task.
   */
  @SuppressWarnings("unchecked")
  public <T extends TaskData> T getData() {

    return (T) data;

  }

  /**
   * Whether the task holds the data of the given kind of user task.
   *
   * <p>
   * It reads the data, so it loads it. When you look through many tasks, ask
   * {@link #isOpen()} first, so that only the data of the open tasks is loaded.
   * </p>
   *
   * @param type The record of a kind of user task.
   * @return Whether this task is of that kind.
   */
  public boolean holds(
      final Class<? extends TaskData> type) {

    return type.isInstance(data);

  }

  /**
   * Whether somebody can still work on the task.
   *
   * @return True until the task is completed or canceled.
   */
  public boolean isOpen() {

    return completedAt == null;

  }

  /**
   * Whether the workflow took the task away before somebody completed it.
   *
   * @return True if the task ended without a user.
   */
  public boolean isCanceled() {

    return (completedAt != null) && (completedBy == null);

  }

  /**
   * Keeps what the form holds right now. It changes this task only, never the workflow
   * aggregate.
   *
   * @param draft The values of the form.
   */
  public void saveDraft(
      final TaskData draft) {

    data = draft;
    updatedAt = OffsetDateTime.now();

  }

  /**
   * Marks the task as completed by a user, with the values sent at the end.
   *
   * @param result The values of the form.
   * @param user   Who completed the task.
   */
  public void complete(
      final TaskData result,
      final String user) {

    final var now = OffsetDateTime.now();
    data = result;
    updatedAt = now;
    completedAt = now;
    completedBy = user;
    addEditor(user);

  }

  /**
   * Notes a user who opened the form to work on the task.
   *
   * @param user Who opened it.
   * @return Whether the user is new in the list. Only then has the cockpit to learn about it.
   */
  public boolean addEditor(
      final String user) {

    if (editors.contains(user)) {
      return false;
    }
    return editors.add(user);

  }

  /**
   * Marks the task as canceled by the workflow. The draft stays, so you can still see what
   * somebody had entered.
   */
  public void cancel() {

    completedAt = OffsetDateTime.now();
    completedBy = null;

  }

}
