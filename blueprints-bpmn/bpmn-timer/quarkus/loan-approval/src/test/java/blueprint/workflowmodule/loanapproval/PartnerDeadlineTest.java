package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule of the deadline, without a BPMS. The integration test proves that the BPMS fires
 * the timer at the moment written here. This test proves that the moment is the one the
 * business means, in winter and in summer.
 */
public class PartnerDeadlineTest {

  private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

  private static final LocalTime FIVE_PM = LocalTime.of(17, 0);

  @Test
  @DisplayName("The deadline ends when the office closes in Vienna, with the winter offset")
  public void inWinter() {

    final var requestedAt = ZonedDateTime.of(2026, 11, 25, 10, 15, 0, 0, VIENNA);

    assertThat(PartnerDeadline.endsAt(requestedAt, "P3D", FIVE_PM))
        .isEqualTo("2026-11-28T17:00:00+01:00");

  }

  @Test
  @DisplayName("The same rule in summer gives the summer offset")
  public void inSummer() {

    final var requestedAt = ZonedDateTime.of(2026, 7, 1, 10, 15, 0, 0, VIENNA);

    assertThat(PartnerDeadline.endsAt(requestedAt, "P3D", FIVE_PM))
        .isEqualTo("2026-07-04T17:00:00+02:00");

  }

  @Test
  @DisplayName("A period which runs out after the office closed ends the next day")
  public void afterTheOfficeClosed() {

    final var requestedAt = ZonedDateTime.of(2026, 11, 25, 18, 30, 0, 0, VIENNA);

    assertThat(PartnerDeadline.endsAt(requestedAt, "P3D", FIVE_PM))
        .isEqualTo("2026-11-29T17:00:00+01:00");

  }

  @Test
  @DisplayName("The machine's time zone plays no part")
  public void theMachineDoesNotMatter() {

    // the same moment, seen from a machine which runs on UTC
    final var requestedAt = ZonedDateTime
        .of(2026, 11, 25, 9, 15, 0, 0, ZoneId.of("UTC"))
        .withZoneSameInstant(VIENNA);

    assertThat(PartnerDeadline.endsAt(requestedAt, "P3D", FIVE_PM))
        .isEqualTo("2026-11-28T17:00:00+01:00");

  }

  @Test
  @DisplayName("Without a closing time the deadline is the end of the period")
  public void withoutAClosingTime() {

    final var requestedAt = ZonedDateTime.of(2026, 11, 25, 10, 15, 0, 0, VIENNA);

    assertThat(PartnerDeadline.endsAt(requestedAt, "PT3S", null))
        .isEqualTo("2026-11-25T10:15:03+01:00");

  }

}
