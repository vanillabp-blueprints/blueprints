package blueprint.loaddriver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * The result of a run, and the one sentence it is for: which queue turned first, and which
 * setting that names.
 *
 * <p>
 * A limit is a knee. The offered load keeps rising from stage to stage, and at some stage the
 * work done stops rising with it. Something queues up at that moment. The queue which started
 * to rise first, and kept rising, is the one which ran out. Every queue names the setting
 * behind it.
 * </p>
 *
 * @param size              The name of the run size.
 * @param profile           The load profile of the run.
 * @param stages            The stages, from the warm up to the drain.
 * @param took              How long the run took.
 * @param unfinished        Workflows which had not ended when the drain gave up.
 * @param failures          Requests and decisions which failed.
 * @param liveSetAfterWarmUp The live set after the warm up, measured after a full collection.
 * @param liveSetAfterDrain  The live set after the drain, measured the same way.
 * @param poolSetting       The setting of the connection pool on this platform.
 */
public record Report(
                     String size,
                     LoadProfile profile,
                     List<StageResult> stages,
                     Duration took,
                     long unfinished,
                     long failures,
                     long liveSetAfterWarmUp,
                     long liveSetAfterDrain,
                     String poolSetting) {

  /** The work done counts as stopped rising below this share of the steady stage. */
  private static final double KNEE = 0.8;

  /** The live set counts as not given back above this factor. */
  private static final double LEAK = 1.5;

  /**
   * A queue the report watches, and the setting it names.
   *
   * @param name    How the report calls it.
   * @param value   Its value in one stage.
   * @param floor   Below this value it does not count as rising, however it moves.
   * @param setting What to change when it is the first one to turn.
   */
  private record Watched(
                         String name,
                         ToDoubleFunction<StageResult> value,
                         double floor,
                         String setting) {
  }

  /**
   * @return Whether every workflow started in the run reached its end.
   */
  public boolean finished() {

    return unfinished == 0;

  }

  /**
   * @return The queue which turned first, if one did.
   */
  public Optional<String> firstQueueToTurn() {

    return firstTurn().map(turn -> turn.watched().name());

  }

  @Override
  public String toString() {

    final var text = new StringBuilder();
    text
        .append(String.format(Locale.ROOT, "Load run '%s' of process '%s', took %ds%n", size,
            profile.process(), took.toSeconds()))
        .append("Hot path: ")
        .append(String.join(" -> ", profile.hotPath()))
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append(String.format(Locale.ROOT, "%-9s %9s %9s %11s %9s %9s %9s %9s %9s %6s %8s%n",
            "stage", "offered/s", "started/s", "delivered/s", "customers", "conn-wait",
            "outbox", "oldest-s", "slots", "gc-%", "live-MB"));
    for (final var stage : stages) {
      text.append(String.format(Locale.ROOT,
          "%-9s %9.1f %9.1f %11.1f %9s %9s %9s %9s %9s %6.1f %8d%n",
          stage.name(),
          stage.offeredPerSecond(),
          stage.startedPerSecond(),
          stage.deliveriesPerSecond(),
          number(stage.startsWaiting()),
          number(stage.connectionsWaiting()),
          number(stage.outboxPending()),
          number(stage.outboxOldestAge()),
          Double.isNaN(stage.slotsInUse())
              ? "-"
              : number(stage.slotsInUse())
                  + "/"
                  + Math.round(stage.slotsConfigured()),
          stage.collectionShare() * 100,
          stage.liveSetBytes() / (1024 * 1024)));
    }
    text.append(System.lineSeparator());

    for (final var line : verdict()) {
      text
          .append(line)
          .append(System.lineSeparator());
    }
    return text.toString();

  }

  /**
   * The sentences of the report, the most important one first.
   */
  private List<String> verdict() {

    final var lines = new ArrayList<String>();

    if (!finished()) {
      lines.add("NOT FINISHED: "
          + unfinished
          + " workflows had not ended when the drain gave up."
          + " Their queues were still full, so read the table, not the verdict.");
    }
    if (failures > 0) {
      lines.add(failures
          + " requests or decisions failed. The log of the driver says why.");
    }

    final var knee = knee();
    final var turn = firstTurn();
    if (turn.isPresent()) {
      lines.add("The queue which turned first: "
          + turn.get().watched().name()
          + ", at stage '"
          + loaded().get(turn.get().stage()).name()
          + "'.");
      lines.add("The setting it names: "
          + turn.get().watched().setting()
          + ".");
      knee.ifPresentOrElse(
          stage -> lines.add("The work done stopped rising with the offered load at stage '"
              + stage.name()
              + "'. So this is a limit, not only a queue which got longer."),
          () -> lines.add("The work done still rose with the offered load. The queue grew,"
              + " but nothing ran out yet. A larger size shows whether it becomes the limit."));
    } else if (knee.isPresent()) {
      lines.add("The work done stopped rising at stage '"
          + knee.get().name()
          + "', and none of the watched queues rose. Look at what this driver cannot see: the"
          + " BPMS itself, or the persons of the load profile, who may be too few for the load.");
    } else {
      lines.add("Nothing ran out at this size. Run the next size to find the limit.");
    }

    if (liveSetAfterDrain > liveSetAfterWarmUp * LEAK) {
      lines.add(String.format(Locale.ROOT, "The live set grew from %d MB after the warm up to %d MB"
          + " after the drain, with no work left. Something keeps what it should let go: a"
          + " defect, not a setting.", liveSetAfterWarmUp / (1024 * 1024),
          liveSetAfterDrain / (1024 * 1024)));
    }

    lines.add("Compare runs of the same machine only. The numbers of another machine, and of a"
        + " shared CI runner above all, move more than a real change would.");
    return lines;

  }

  private List<Watched> watched() {

    return List.of(
        new Watched(
            "threads waiting for a database connection", StageResult::connectionsWaiting, 0.5, "the connection pool, '"
                + poolSetting
                + "', or the number of threads which draw"
                + " from it: the worker threads of the adapter and"
                + " 'vanillabp.outbox.dispatch-threads'"),
        new Watched(
            "the age of the oldest outbox entry", StageResult::outboxOldestAge, 1.0, "the dispatch stage of the outbox, 'vanillabp.outbox.dispatch-threads'"),
        new Watched(
            "execution slots of the adapter in use", stage -> stage.slotsInUse() / stage
                .slotsConfigured(), 0.9, "the worker threads of the adapter, 'vanillabp.adapters.<adapter-id>.worker-threads'"),
        new Watched(
            "time spent collecting garbage", StageResult::collectionShare, 0.10, "the heap, '-Xmx', if the live set in the table stays flat. Then the heap is too"
                + " small for the work in flight. If the live set grows from stage to stage as"
                + " well, something keeps what it should let go"));

  }

  /** The steady stage, the steps and the peak: the stages the offered load rises over. */
  private List<StageResult> loaded() {

    return stages.subList(1, stages.size() - 1);

  }

  private record Turn(
                      Watched watched,
                      int stage) {
  }

  /**
   * The queue which started rising first and kept rising until the peak. A queue counts as
   * rising at a stage where it is above its floor and higher than in the stage before. The
   * steady stage is the baseline, so it never counts.
   */
  private Optional<Turn> firstTurn() {

    final var loaded = loaded();
    Turn first = null;
    for (final var watched : watched()) {
      for (var i = 1; i < loaded.size(); ++i) {
        if (risesFrom(watched, loaded, i)) {
          if (first == null || i < first.stage()) {
            first = new Turn(watched, i);
          }
          break;
        }
      }
    }
    return Optional.ofNullable(first);

  }

  private static boolean risesFrom(
      final Watched watched,
      final List<StageResult> loaded,
      final int stage) {

    final var before = watched.value().applyAsDouble(loaded.get(stage - 1));
    final var at = watched.value().applyAsDouble(loaded.get(stage));
    if (Double.isNaN(at) || at <= watched.floor() || !(Double.isNaN(before) || at > before)) {
      return false;
    }
    for (var later = stage + 1; later < loaded.size(); ++later) {
      final var value = watched.value().applyAsDouble(loaded.get(later));
      if (Double.isNaN(value) || value <= watched.floor()) {
        return false;
      }
    }
    return true;

  }

  /**
   * The first stage after the steady one from which on the work done per offered workflow
   * stayed below {@value #KNEE} of what the steady stage did, until the peak, for two stages
   * at least. A single stage below it is not a knee: the payout run wakes its workflows in
   * bursts, so the work done of one stage moves more than that of a run of stages.
   */
  private Optional<StageResult> knee() {

    final var loaded = loaded();
    final var steady = loaded.getFirst();
    final var steadyEfficiency = steady.deliveriesPerSecond() / steady.offeredPerSecond();
    for (var stage = 1; stage < loaded.size() - 1; ++stage) {
      final var staysBelow = loaded
          .subList(stage, loaded.size())
          .stream()
          .allMatch(later -> later.deliveriesPerSecond() / later.offeredPerSecond() < steadyEfficiency * KNEE);
      if (staysBelow) {
        return Optional.of(loaded.get(stage));
      }
    }
    return Optional.empty();

  }

  private static String number(
      final double value) {

    return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.1f", value);

  }

}
