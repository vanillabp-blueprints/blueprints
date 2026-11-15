package blueprint.simulator.loadrun;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names the threads of the driver, so a thread dump of the simulator shows at once what each
 * thread plays.
 */
public final class Threads {

  private Threads() {
  }

  public static ThreadFactory named(
      final String role) {

    final var count = new AtomicInteger();
    return runnable -> {
      final var thread = new Thread(runnable, "load-"
          + role
          + "-"
          + count.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };

  }

}
