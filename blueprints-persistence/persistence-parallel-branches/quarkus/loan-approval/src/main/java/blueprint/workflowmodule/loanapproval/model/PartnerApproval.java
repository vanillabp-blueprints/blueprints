package blueprint.workflowmodule.loanapproval.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the branch waiting for the partner writes: the id of the open task while it waits,
 * and the answer once it arrives.
 *
 * <p>
 * A row of its own, because this branch runs while the other one does. The transaction
 * writing it belongs to the application: the partner answers through the API, and that call
 * completes the task.
 * </p>
 */
@Entity
@Table(name = "PARTNER_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerApproval {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * The BPMS-side id of the open task. It belongs here rather than on the aggregate: the
   * other branch would write the aggregate's row from a copy read before this id existed.
   */
  @Column
  private String taskId;

  /** Who approved, once the partner answered. */
  @Column
  private String approvedBy;

  /** When the answer arrived. */
  @Column
  private OffsetDateTime approvedAt;

}
