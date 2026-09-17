package com.acme.loanrepayment.model;

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
 * The workflow aggregate of the repayment.
 *
 * <p>
 * The entity is given a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and every use case of the reference structure has a class
 * of that name - so the second module in an application says which entity it is.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so none of its attributes is written to
 * the BPMS. Nothing in the model asks for one: the process runs from its start event
 * through a single service task to its end event, and there is no condition or timer to
 * evaluate. That is why no attribute carries {@code @SyncWithBPMS}. The BPMS is given the
 * aggregate's ID, which VanillaBP always shares because that is how it finds the workflow
 * again.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity(name = "LoanRepayment")
@Table(name = "LOAN_REPAYMENT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /**
   * The natural id of the use case.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String repaymentId;

  /** The customer paying back, looked up in the shared customer directory. */
  @Column
  private String customerId;

  /** The amount owed. */
  @Column
  private Integer amount;

  /** Filled by the business code the service task of the process triggers. */
  @Column
  private Integer installments;

}
