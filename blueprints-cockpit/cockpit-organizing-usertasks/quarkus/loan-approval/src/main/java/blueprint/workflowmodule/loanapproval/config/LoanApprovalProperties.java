package blueprint.workflowmodule.loanapproval.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

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
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-approval")
public interface LoanApprovalProperties {

  /** The highest credit rating the rating step may award. */
  @WithDefault("100")
  int ratingScale();

  /** How long the advisor has to call the customer back, counted from the request. */
  @WithDefault("P1D")
  Duration callCustomerWithin();

  /** How long the risk team has for its assessment, counted from the request. */
  @WithDefault("P2D")
  Duration assessRiskWithin();

  /**
   * By when the bank promises the customer an answer, counted from the request. The approval
   * is due then.
   */
  @WithDefault("P5D")
  Duration answerPromisedWithin();

  /** When a customer who was not reached is called again, on the next day. */
  @WithDefault("09:00")
  LocalTime callAgainAt();

  /** The time zone in which "the next day" and "9 o'clock" are meant. */
  @WithDefault("Europe/Vienna")
  ZoneId zone();

  /**
   * The address of the CRM, the application in which advisors call their customers. The
   * cockpit opens the user task "Call customer" there and not in a form of this module.
   */
  @WithDefault("https://crm.example.com")
  String crmAddress();

}
