package blueprint.workflowmodule.ratewatch.model;

import java.time.LocalDateTime;

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
 * The workflow aggregate of the rate watch, a second use case in the same workflow module.
 *
 * <p>
 * The entity is given a name of its own. Two JPA entities called {@code Aggregate} in one
 * persistence unit would clash, and the reference structure gives every use case a class of
 * that name - so the second one in a module says which entity it is.
 * </p>
 *
 * <p>
 * The class is annotated {@code @NoSyncWithBPMS}, so nothing here is written to the BPMS.
 * The model waits for a signal name and then runs one task, and no expression in it reads
 * the aggregate. What the BPMS holds is the workflow aggregate's ID, which VanillaBP always
 * shares because that is how it finds the workflow again.
 * </p>
 */
@Entity(name = "RateWatch")
@Table(name = "RATE_WATCH")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NoSyncWithBPMS
public class Aggregate {

  /** The natural id of this watch. */
  @Id
  private String watchId;

  /**
   * When the signal arrived. A value here means the workflow passed the catch event, which
   * is the whole point of this process: it was never addressed, and it continued anyway.
   */
  @Column
  private LocalDateTime noticedAt;

}
