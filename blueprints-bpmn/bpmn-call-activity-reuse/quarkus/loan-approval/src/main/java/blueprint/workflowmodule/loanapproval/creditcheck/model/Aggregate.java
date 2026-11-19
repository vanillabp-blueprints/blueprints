package blueprint.workflowmodule.loanapproval.creditcheck.model;

import io.vanillabp.spi.service.NoSyncWithBPMS;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate of the credit check. Several processes use the credit check, each in
 * a context of its own, so it cannot share the aggregate of one of them. It has this one.
 *
 * <p>
 * It keeps who asked and for which case. It does not know what a loan approval or an
 * overdraft is. Both are only strings here, handed back with the result.
 * </p>
 *
 * <p>
 * The entity is given a name of its own. Three JPA entities called {@code Aggregate} in one
 * persistence unit would clash otherwise.
 * </p>
 */
@Entity(name = "CreditCheck")
@Table(name = "CREDIT_CHECK")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /** The natural id of the credit check. */
  @Id
  private String creditCheckId;

  /** Who asked for the check. */
  @Column
  private String requestedBy;

  /** The case of the one who asked. */
  @Column
  private String caseId;

  /** The amount the credit is checked for. */
  @Column
  private Integer amount;

  /** Written by the first service task, once the agency was asked. */
  @Column
  private Boolean agencyAsked;

  /** What the agency answered. Written before the message is correlated. */
  @Column
  private Integer score;

  /** Written by the last service task, once the result was reported. */
  @Column
  private Boolean reported;

}
