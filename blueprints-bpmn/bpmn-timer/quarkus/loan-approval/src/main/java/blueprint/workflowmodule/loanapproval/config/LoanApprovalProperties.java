package blueprint.workflowmodule.loanapproval.config;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


/**
 * Configuration of this workflow module. Its values come from
 * {@code loan-approval/loan-approval.yaml} - a configuration file the workflow module
 * brings along itself, so that everything the module needs stays inside the module.
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

  /**
   * How long the partner may take to answer, as an ISO-8601 duration. It is a business
   * value, so it is configuration and not a literal in the model.
   */
  @WithDefault("P3D")
  String partnerDeadline();

  /**
   * When the office closes. The partner's time to answer runs until then, on the day the
   * {@link #partnerDeadline} runs out. Empty means the deadline is the end of the period.
   */
  Optional<LocalTime> officeClosesAt();

  /**
   * The time zone of the business. Every time of day of this module is a time in this zone,
   * whatever zone the machine runs in.
   */
  @WithDefault("Europe/Vienna")
  ZoneId zone();

}
