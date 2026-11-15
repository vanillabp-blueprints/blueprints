package blueprint.workflowmodule;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Base class for the integration test of a workflow module. It boots the workflow module,
 * a database and a BPMS adapter, and adds the one thing such a test always needs: waiting
 * for a workflow to have made progress.
 *
 * <p>
 * <strong>Assert on the workflow aggregate, never on the engine.</strong> The aggregate is
 * the state of the business case, and it is the only thing that means the same on every
 * BPMS. A test reaching into engine internals stops being a test of your application and
 * starts being a test of that engine - and it will not survive the next one.
 * </p>
 *
 * <p>
 * <strong>Wait, do not assert immediately.</strong> A BPMS runs tasks in transactions of
 * its own, and a remote one does so eventually. Asserting right after having started a
 * workflow passes on an embedded engine and fails on a remote one, which is the kind of
 * test that makes people believe the BPMS is unreliable.
 * </p>
 *
 * <p>
 * Every test class extending this one carries {@code @QuarkusTest} itself. The annotation
 * is what makes the test class a bean of the application under test, and that only happens
 * for the class carrying it, not for the ones inheriting from it.
 * </p>
 *
 * <p>
 * Do not annotate subclasses with {@code @Transactional}: the polling below has to see the
 * writes of the BPMS' own transactions, which a test-managed transaction would hide.
 * </p>
 *
 * <p>
 * Every subclass also runs {@link #theTestRunsOnTheBpmsOfTheMavenBuild()}. It checks that
 * the test runs on the BPMS the Maven build chose.
 * </p>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/quarkus/} of the monorepo. Do not edit it here.
 * </p>
 */
public abstract class WorkflowModuleTest {

  /**
   * How long a test waits for what VanillaBP and the blueprint do themselves: a reply to a
   * call of the API, a handler which ran, an aggregate which was written. Generous on
   * purpose, so that a slow CI machine does not turn into a flaky test.
   *
   * <p>
   * It is one minute, so that ONE retry of the outbox fits in. Two engine commands for the
   * same workflow may collide, for example a message which is correlated while the user
   * task is completed. Camunda 7 then rejects one of them with an optimistic locking error,
   * and the outbox tries it again after {@code vanillabp.outbox.attempt-frequency}, which is
   * thirty seconds by default. That is expected behaviour and not a fault, so it must not
   * fail a test. The second retry comes a minute after the first one, and that is beyond
   * this time. So a step which fails again and again still fails the test. A test which
   * passes is not slower, because the wait ends as soon as the condition holds.
   * </p>
   *
   * <p>
   * Use {@link #SCHEDULER_TIMEOUT} instead if the test waits for a timer of the model.
   * </p>
   */
  protected static final Duration TIMEOUT = Duration.ofMinutes(1);

  /**
   * How long a test waits for what the scheduler of a BPMS has to do: a timer which is due
   * and has to fire. This is much longer than {@link #TIMEOUT}, because here the test does
   * not wait for code. It waits for a pool of threads in the engine, and when many builds
   * run on one machine, they all share it. A test which waits for a scheduler should fail
   * because the thing did not happen, not because the machine was busy.
   *
   * <p>
   * Choose it only for the steps which wait for a timer, and pass it to
   * {@code awaitAggregate}. Every other step keeps {@link #TIMEOUT}, so that a real fault
   * still shows up quickly.
   * </p>
   */
  protected static final Duration SCHEDULER_TIMEOUT = Duration.ofMinutes(2);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  /**
   * The system property which carries the BPMS profile of the Maven build. The blueprint POM
   * sets it for the tests, because Quarkus reads the configuration of that BPMS through it.
   */
  private static final String BPMS_OF_THE_BUILD = "quarkus.config.profile.parent";

  /** The file every Quarkus extension describes itself with. */
  private static final String EXTENSION_DESCRIPTOR = "META-INF/quarkus-extension.properties";

  /** What the capability of every BPMS adapter starts with. The name of its BPMS follows. */
  private static final String ADAPTER_CAPABILITY = "io.vanillabp.adapter.";

  /**
   * Checks that the test runs on the BPMS the Maven build chose.
   *
   * <p>
   * A Quarkus test does not take its classpath from Maven. It reads the POM again and builds
   * the application on its own, so it may come to other active profiles. An example: a
   * profile of the POM switches itself on when a system property is missing. Maven then
   * switches off the default BPMS profile and has no BPMS adapter. The test JVM has that
   * property, so there the profile stays off, and the test finds the default adapter. The
   * test passes on a BPMS the build does not have.
   * </p>
   *
   * <p>
   * The blueprint POM hands the BPMS profile of the build to every test, as
   * {@value #BPMS_OF_THE_BUILD}, first in its list of profiles. It is {@code none} if no BPMS
   * profile was active. A test
   * started by the IDE has no such property, and then there is nothing to compare.
   * </p>
   *
   * @throws Exception If an extension descriptor cannot be read.
   */
  @Test
  public void theTestRunsOnTheBpmsOfTheMavenBuild() throws Exception {

    final var profiles = System.getProperty(BPMS_OF_THE_BUILD);
    assumeTrue(
        profiles != null,
        "Not started by Maven, so there is no BPMS profile to compare the adapters with.");
    // The property is a list of Quarkus profiles, e.g. "camunda7,local" where an application
    // adds a profile of its own. The BPMS profile always comes first.
    final var bpms = profiles.split(",")[0].trim();

    final var loaded = loadedBpmsAdapters();
    assertTrue(
        loaded.contains(bpms),
        () -> "Maven built with the BPMS profile '"
            + bpms
            + "', but this test loads the adapters "
            + loaded
            + ". A Quarkus test reads the POM on its own, and it chose other profiles than Maven."
            + (bpms.equals("none")
                ? " Most likely another profile of the POM switched itself on, so Maven"
                    + " switched off the default BPMS profile."
                    + " bin/check_default_profiles.py finds such a profile."
                : " Name the BPMS profile with -P on the Maven command line. The test reads"
                    + " -P from there only, not from MAVEN_ARGS or settings.xml."));

  }

  /**
   * The BPMS adapters this test runs on. Every BPMS adapter is a Quarkus extension, and it
   * provides a capability which names its BPMS, e.g. {@code io.vanillabp.adapter.camunda7}.
   * VanillaBP finds its adapters by the same capabilities.
   *
   * @return The BPMS of each adapter, e.g. {@code camunda7}.
   * @throws Exception If an extension descriptor cannot be read.
   */
  private static Set<String> loadedBpmsAdapters() throws Exception {

    final var adapters = new TreeSet<String>();
    final var descriptors = WorkflowModuleTest.class
        .getClassLoader()
        .getResources(EXTENSION_DESCRIPTOR);

    while (descriptors.hasMoreElements()) {
      final var descriptor = new Properties();
      try (var input = descriptors
          .nextElement()
          .openStream()) {
        descriptor.load(input);
      }
      for (final var capability : descriptor
          .getProperty("provides-capabilities", "")
          .split(",")) {
        final var name = capability.trim();
        if (name.startsWith(ADAPTER_CAPABILITY)) {
          adapters.add(name.substring(ADAPTER_CAPABILITY.length()));
        }
      }
    }

    return adapters;

  }

  /**
   * Waits until the workflow aggregate exists and satisfies the given condition.
   *
   * @param <A>       The type of the workflow aggregate.
   * @param load      Loads the workflow aggregate by its ID, e.g. the repository's
   *                  {@code findById}.
   * @param id        The aggregate's ID, which is the natural ID of the business case.
   * @param condition What the process is expected to have achieved.
   * @return The aggregate as it satisfied the condition.
   */
  protected <A> A awaitAggregate(
      final Function<String, Optional<A>> load,
      final String id,
      final Predicate<A> condition) {

    return awaitAggregate(load, id, TIMEOUT, condition);

  }

  /**
   * Waits as long as the given time until the workflow aggregate exists and satisfies the
   * given condition. Pass {@link #SCHEDULER_TIMEOUT} if the condition needs a timer of the
   * model to fire.
   *
   * @param <A>       The type of the workflow aggregate.
   * @param load      Loads the workflow aggregate by its ID.
   * @param id        The aggregate's ID, which is the natural ID of the business case.
   * @param timeout   How long to wait, {@link #TIMEOUT} or {@link #SCHEDULER_TIMEOUT}.
   * @param condition What the process is expected to have achieved.
   * @return The aggregate as it satisfied the condition.
   */
  protected <A> A awaitAggregate(
      final Function<String, Optional<A>> load,
      final String id,
      final Duration timeout,
      final Predicate<A> condition) {

    final var lastSeen = new AtomicReference<Optional<A>>(Optional.empty());

    try {

      await()
          .atMost(timeout)
          // The first look comes at once. Awaitility would otherwise wait one poll interval
          // before it, and that pause could hide a test which only passes because the BPMS
          // had time to catch up.
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            // A fresh transaction per poll - otherwise the writes of the BPMS' own
            // transactions would never become visible.
            final var found = QuarkusTransaction
                .requiringNew()
                .call(() -> load.apply(id));
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The workflow '"
              + id
              + "' did not reach the expected state within "
              + timeout
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("no aggregate at all - was the workflow started?"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  /**
   * Waits until the workflow aggregate exists at all. Useful right after having started a
   * workflow.
   *
   * @param <A>  The type of the workflow aggregate.
   * @param load Loads the workflow aggregate by its ID.
   * @param id   The aggregate's ID.
   * @return The aggregate.
   */
  protected <A> A awaitAggregate(
      final Function<String, Optional<A>> load,
      final String id) {

    return awaitAggregate(load, id, aggregate -> true);

  }

}
