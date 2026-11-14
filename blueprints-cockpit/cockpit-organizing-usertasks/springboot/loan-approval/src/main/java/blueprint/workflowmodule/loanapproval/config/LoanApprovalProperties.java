package blueprint.workflowmodule.loanapproval.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml}, a configuration file the workflow module brings
 * along itself. So everything the module needs stays inside the module.
 *
 * <p>
 * Most values are business rules about time: by when a user task is due, and when a customer
 * who was not reached is called again. They are rules of the bank, not of the BPMS, so they
 * live here and the details of a user task are computed from them.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "loan-approval")
@Data
public class LoanApprovalProperties {

  /** The highest credit rating the rating step may award. */
  private int ratingScale = 100;

  /** How long the advisor has to call the customer back, counted from the request. */
  private Duration callCustomerWithin = Duration.ofDays(1);

  /** How long the risk team has for its assessment, counted from the request. */
  private Duration assessRiskWithin = Duration.ofDays(2);

  /**
   * By when the bank promises the customer an answer, counted from the request. The approval
   * is due then.
   */
  private Duration answerPromisedWithin = Duration.ofDays(5);

  /** When a customer who was not reached is called again, on the next day. */
  private LocalTime callAgainAt = LocalTime.of(9, 0);

  /** The time zone in which "the next day" and "9 o'clock" are meant. */
  private ZoneId zone = ZoneId.of("Europe/Vienna");

  /**
   * The address of the CRM, the application in which advisors call their customers. The
   * cockpit opens the user task "Call customer" there and not in a form of this module.
   */
  private String crmAddress = "https://crm.example.com";

}
