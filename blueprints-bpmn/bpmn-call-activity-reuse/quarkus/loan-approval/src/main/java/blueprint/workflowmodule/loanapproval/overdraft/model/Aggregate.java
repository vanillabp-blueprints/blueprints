package blueprint.workflowmodule.loanapproval.overdraft.model;

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
 * The workflow aggregate of the overdraft request, the second process which uses the credit
 * check. Its context is a current account, not a loan, and it uses the score in its own way.
 *
 * <p>
 * The entity is given a name of its own. Three JPA entities called {@code Aggregate} in one
 * persistence unit would clash otherwise.
 * </p>
 */
@Entity(name = "OverdraftRequest")
@Table(name = "OVERDRAFT_REQUEST")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /** The natural id of the overdraft request. */
  @Id
  private String overdraftId;

  /** The overdraft limit the customer asked for. */
  @Column
  private Integer requestedLimit;

  /** The credit check this overdraft request asked for. Written by the first service task. */
  @Column
  private String creditCheckId;

  /** The score the credit check reported. Written before the message is correlated. */
  @Column
  private Integer creditScore;

  /** The limit granted, written by the service task behind the message event. */
  @Column
  private Integer grantedLimit;

}
