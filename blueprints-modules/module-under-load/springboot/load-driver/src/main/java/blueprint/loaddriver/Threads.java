package blueprint.loaddriver;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names the threads of the driver, so a thread dump shows at once which threads are the
 * driver's and which belong to the application.
 */
final class Threads {

  private Threads() {
  }

  static ThreadFactory named(
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
