package blueprint.workflowmodule.loanapproval.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.annotations.DynamicUpdate;

import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import io.vanillabp.spi.service.NoSyncWithBPMS;
import io.vanillabp.spi.service.SyncWithBPMS;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKey;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the process
 * needs to know. There are no process variables. This is the single source of truth, and it
 * stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * This blueprint is about {@link #tasks}: every user task of the workflow, open or done, as a
 * row of its own. The aggregate holds the results of user tasks, {@link #riskAcceptable} and
 * {@link #documentChecks}, and nothing else of them. A result is written here when its user
 * task is completed, never before. A draft stays in its {@link Task}.
 * </p>
 *
 * <p>
 * Two branches of the workflow run at the same time, and both write to the aggregate when a
 * user task is completed. {@code @DynamicUpdate} makes the persistence layer write only the
 * columns which changed. The risk assessment writes its own columns, a document check writes a
 * row of its own table, so neither of them puts back an old value of the other. A draft does
 * not write the aggregate at all.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so nothing goes to the BPMS unless it says
 * otherwise. Two things do: the documents the multi-instance task iterates over, and the
 * answer the gateway behind the risk assessment asks for.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@DynamicUpdate
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated one
   * makes a workflow started twice for the same business case a detectable duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The documents to check, written when the loan is requested. The workflow opens one user
   * task "Check document" per entry.
   *
   * <p>
   * Annotated {@code @SyncWithBPMS} because the model iterates over it, and the engine hands
   * each instance of the task one element. Loaded eagerly, because VanillaBP reads it whenever
   * it hands the aggregate to the BPMS.
   * </p>
   */
  @SyncWithBPMS
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_DOCUMENT", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @OrderColumn(name = "POSITION")
  @Column(name = "DOCUMENT_ID")
  @Builder.Default
  private List<String> documentIds = new ArrayList<>();

  /**
   * Every user task of this loan approval, by the id the BPMS gave it. A task is added when
   * the BPMS opens it and stays when it is done.
   *
   * <p>
   * A map and not one attribute per task, because a task definition may be open several times
   * at once. The user task "Check document" is open once per document, and only the task id
   * tells the instances apart.
   * </p>
   *
   * <p>
   * The tasks are rows of a table of their own. Saving a draft changes the row of that task
   * and leaves the row of the aggregate alone. So two users who work on two tasks of the same
   * loan approval at the same time do not overwrite each other.
   * </p>
   *
   * <p>
   * Loaded lazily, when somebody reads it. A service task, a gateway or the start of the
   * workflow never needs the tasks, and the list grows with every task the workflow opens. So
   * read the tasks inside a transaction, as {@code Service} does. Left out of
   * {@code equals} and {@code hashCode}, which would load them.
   * </p>
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @MapKey(name = "taskId")
  @JoinTable(
      name = "LOAN_APPROVAL_TASKS",
      joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"),
      inverseJoinColumns = @JoinColumn(name = "TASK_ID"))
  @Builder.Default
  @EqualsAndHashCode.Exclude
  private Map<String, Task> tasks = new HashMap<>();

  /**
   * The result of the user task "Assess risk". Empty until that task is completed, however
   * many drafts were saved before.
   */
  @Column
  private Boolean riskAcceptable;

  /** Why the risk was assessed as it was, written together with {@link #riskAcceptable}. */
  @Column
  private String riskComment;

  /**
   * The results of the user tasks "Check document": whether each document is in order, by
   * document. An entry is written when the task of that document is completed.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "LOAN_APPROVAL_DOCUMENT_CHECK", joinColumns = @JoinColumn(name = "LOAN_REQUEST_ID"))
  @MapKeyColumn(name = "DOCUMENT_ID")
  @Column(name = "VALID")
  @Builder.Default
  private Map<String, Boolean> documentChecks = new HashMap<>();

  /** How the loan request ended: approved, rejected, or empty while the workflow runs. */
  @Column
  private Boolean loanApproved;

  /**
   * The user task with the given id.
   *
   * @param taskId The id the BPMS gave the task.
   * @return The task, or nothing if this loan approval has no task of that id.
   */
  public Optional<Task> getTask(
      final String taskId) {

    return Optional.ofNullable(tasks.get(taskId));

  }

  /**
   * Adds a user task the BPMS just opened.
   *
   * @param task The new task.
   */
  public void addTask(
      final Task task) {

    tasks.put(task.getTaskId(), task);

  }

  /**
   * What the gateway behind the risk assessment asks. It reads the result of the completed
   * task, so a draft can never send the workflow one way or the other.
   *
   * <p>
   * Annotated {@code @SyncWithBPMS} because a BPMS evaluates the condition against what it
   * holds, and a remote one only holds what was sent to it.
   * </p>
   *
   * @return Whether the risk was assessed as acceptable.
   */
  @SyncWithBPMS
  public boolean isRiskAccepted() {

    return Boolean.TRUE.equals(riskAcceptable);

  }

}
