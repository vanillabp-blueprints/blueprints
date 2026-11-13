package blueprint.workflowmodule.loanapproval.model;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import blueprint.workflowmodule.loanapproval.model.usertask.Task;
import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
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
 * Most of what the Business Cockpit shows about a user task is read from here: who caused the
 * last change ({@link #lastActionBy}), who the advisor of the customer is ({@link #advisor}),
 * who assessed the risk ({@link #riskAssessedBy}), and by when the bank promised an answer
 * ({@link #answerPromisedBy}). No BPMS records any of this, and the security context of a
 * request is gone by the time a report is built. So the module writes it down while it still
 * knows it.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}. The model has no gateway, so the BPMS needs
 * nothing of the aggregate.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
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

  /** The name of the customer. */
  @Column
  private String customer;

  /** The phone number of the customer. No list shows it, but the search finds it. */
  @Column
  private String phone;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /** When the loan was requested. The due dates of the user tasks are counted from here. */
  @Column
  private OffsetDateTime requestedAt;

  /** By when the bank promised the customer an answer. The approval is due then. */
  @Column
  private OffsetDateTime answerPromisedBy;

  /**
   * The advisor of the customer, who calls the customer back. The user task "Call customer" is
   * assigned to this user. At first it is the user who requested the loan.
   */
  @Column
  private String advisor;

  /**
   * The user who caused the latest change of this loan approval: who requested it, who
   * completed a task, who handed the customer to another advisor. Every report to the cockpit
   * names this user as its initiator, and the cockpit does not notify somebody about their own
   * work.
   */
  @Column
  private String lastActionBy;

  /**
   * Every user task of this loan approval, by the id the BPMS gave it. A task is added when
   * the BPMS opens it and stays when it is done.
   *
   * <p>
   * The tasks are rows of a table of their own. Each of them also keeps who worked on it,
   * which the cockpit gets as the admitted users of the task.
   * </p>
   *
   * <p>
   * Loaded lazily, when somebody reads it. A service task or the start of the workflow never
   * needs the tasks, and the list grows with every task the workflow opens. So read the tasks
   * inside a transaction, as {@code Service} does. Left out of {@code equals} and
   * {@code hashCode}, which would load them.
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

  /** What the customer said on the phone, the result of the user task "Call customer". */
  @Column
  private String customerNote;

  /** The result of the user task "Assess risk". Empty until that task is completed. */
  @Column
  private Boolean riskAcceptable;

  /** Why the risk was assessed as it was, written together with {@link #riskAcceptable}. */
  @Column
  private String riskComment;

  /**
   * Who assessed the risk. This user must not approve the loan as well. The cockpit gets the
   * user as an excluded candidate of "Approve loan", and the module refuses the approval from
   * this user.
   */
  @Column
  private String riskAssessedBy;

  /** How the loan request ended: approved, rejected, or empty while the workflow runs. */
  @Column
  private Boolean loanApproved;

  /** Who approved or rejected the loan. */
  @Column
  private String decidedBy;

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

}
