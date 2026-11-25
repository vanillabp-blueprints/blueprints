package blueprint.workflowmodule.loanapproval;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Works out when the partner's time to answer ends. The rule is a rule of the business, so
 * it is counted in the time zone of the business, never in the time zone of the machine.
 *
 * <p>
 * The partner gets a period, such as three days, and then the rest of that day until the
 * office closes. "The office closes at 17:00" means 17:00 in Vienna, whether the
 * application runs in Vienna, in a data centre in Frankfurt or in a container which runs on
 * UTC. Java knows the rules of every time zone, summer time included. So the zone is
 * configuration of the module, and the calculation happens here.
 * </p>
 *
 * <p>
 * The result is a point in time written with its offset, such as
 * {@code 2026-11-28T17:00:00+01:00}. The timer of the model reads it as it is. A date with
 * an offset names one moment, and every BPMS fires the timer at that moment. A date without
 * one, such as {@code 2026-11-28T17:00:00}, is read in the time zone of the machine, or
 * refused, depending on the BPMS. Neither is the zone of the business.
 * </p>
 */
public final class PartnerDeadline {

  private PartnerDeadline() {
    // only the static method
  }

  /**
   * The end of the partner's time to answer.
   *
   * @param requestedAt  When the loan was requested, in the time zone of the business.
   * @param period       How long the partner may take, as an ISO-8601 duration such as
   *                     {@code P3D}.
   * @param officeClosesAt When the office closes on the day the period runs out. If the period
   *                     runs out after that time, the deadline is the same time a day later.
   *                     {@code null} means: the deadline is the end of the period itself.
   * @return The deadline as an ISO-8601 date and time with offset.
   */
  public static String endsAt(
      final ZonedDateTime requestedAt,
      final String period,
      final LocalTime officeClosesAt) {

    final var periodEnds = requestedAt.plus(Duration.parse(period));
    if (officeClosesAt == null) {
      return format(periodEnds);
    }

    // 'with' keeps the date and the zone, and picks the right offset for that date, so a
    // deadline in July gets +02:00 and one in November +01:00
    final var officeClosesThatDay = periodEnds.with(officeClosesAt);
    if (officeClosesThatDay.isBefore(periodEnds)) {
      return format(officeClosesThatDay.plusDays(1));
    }
    return format(officeClosesThatDay);

  }

  private static String format(
      final ZonedDateTime deadline) {

    return deadline
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

  }

}
