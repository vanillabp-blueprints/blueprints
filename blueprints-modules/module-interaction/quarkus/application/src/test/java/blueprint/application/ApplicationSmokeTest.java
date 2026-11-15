package blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.junit.QuarkusTest;
import io.vanillabp.spi.process.ProcessService;
import jakarta.enterprise.util.TypeLiteral;

/**
 * The smoke test of the application: does it start, is every workflow module found, and is
 * every BPMN task wired to code?
 *
 * <p>
 * Booting is the bigger half of the assertion. VanillaBP validates the wiring between BPMN
 * and code while the application starts, so an application which comes up means every BPMN
 * task has its {@code @WorkflowTask} method and the other way round. A failing start is a
 * real finding, and its message says what is missing.
 * </p>
 *
 * <p>
 * What booting cannot notice is a workflow module which is simply not there: a JAR left out
 * of the dependencies, a marker file missing after a rename, or a module whose classes never
 * made it into an index. The application starts perfectly well without it. So the test
 * counts: every workflow module declaring itself on the classpath has to be served by a
 * {@code ProcessService}, and the modules are read from the same marker files VanillaBP
 * reads.
 * </p>
 *
 * <p>
 * An application which is its own workflow module has no marker file at all. There is
 * nothing to count then, and the assertion falls back to what it can say: something has to
 * be wired.
 * </p>
 *
 * <p>
 * A smoke test exists in addition to the tests of the workflow modules because several
 * modules in one runtime can interfere in ways an isolated module test never sees.
 * </p>
 *
 * <p>
 * A second test checks that the application runs on the BPMS the Maven build chose.
 * </p>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/quarkus/} of the monorepo. Do not edit it here.
 * </p>
 */
@QuarkusTest
public class ApplicationSmokeTest {

  /** The file a workflow module declares itself with; it contains the module's ID. */
  private static final String WORKFLOW_MODULE_MARKER = "META-INF/workflow-module";

  /**
   * The system property which carries the BPMS profile of the Maven build. The blueprint POM
   * sets it for the tests, because Quarkus reads the configuration of that BPMS through it.
   */
  private static final String BPMS_OF_THE_BUILD = "quarkus.config.profile.parent";

  /** The file every Quarkus extension describes itself with. */
  private static final String EXTENSION_DESCRIPTOR = "META-INF/quarkus-extension.properties";

  /** What the capability of every BPMS adapter starts with. The name of its BPMS follows. */
  private static final String ADAPTER_CAPABILITY = "io.vanillabp.adapter.";

  @Test
  public void theApplicationStartsAndEveryWorkflowModuleIsWired() throws Exception {

    final var declared = declaredWorkflowModules();
    // by the parameterized type: a ProcessService bean is typed by its workflow aggregate,
    // and the raw class matches none of them
    final var wired = Arc
        .container()
        .listAll(new TypeLiteral<ProcessService<?>>() {
        })
        .stream()
        .map(InstanceHandle::get)
        .map(ProcessService::getWorkflowModuleId)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (declared.isEmpty()) {

      assertThat(wired)
          .describedAs(
              "No ProcessService bean exists, so no workflow module was detected."
                  + " Check the module's dependency, its "
                  + WORKFLOW_MODULE_MARKER
                  + " file and the index its build writes.")
          .isNotEmpty();
      return;

    }

    assertThat(wired)
        .describedAs(
            "Every workflow module on the classpath has to be served by a ProcessService."
                + " Declared: "
                + declared
                + ", wired: "
                + wired
                + ". A module which is declared but not wired is missing its index;"
                + " one which is wired but not declared has no "
                + WORKFLOW_MODULE_MARKER
                + " file.")
        .containsExactlyInAnyOrderElementsOf(declared);

  }

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
   * {@value #BPMS_OF_THE_BUILD}. It is {@code none} if no BPMS profile was active. A test
   * started by the IDE has no such property, and then there is nothing to compare.
   * </p>
   *
   * @throws Exception If an extension descriptor cannot be read.
   */
  @Test
  public void theTestRunsOnTheBpmsOfTheMavenBuild() throws Exception {

    final var bpms = System.getProperty(BPMS_OF_THE_BUILD);
    assumeTrue(
        bpms != null,
        "Not started by Maven, so there is no BPMS profile to compare the adapters with.");

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
    final var descriptors = ApplicationSmokeTest.class
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
   * The workflow modules which declare themselves on the classpath, read from the marker
   * files VanillaBP reads.
   *
   * @return The module IDs, empty if the application is its own workflow module.
   * @throws Exception If a marker file cannot be read.
   */
  private static Set<String> declaredWorkflowModules() throws Exception {

    final var modules = new LinkedHashSet<String>();
    final var markers = Thread
        .currentThread()
        .getContextClassLoader()
        .getResources(WORKFLOW_MODULE_MARKER);

    while (markers.hasMoreElements()) {
      try (var reader = new BufferedReader(
          new InputStreamReader(
              markers
                  .nextElement()
                  .openStream(), StandardCharsets.UTF_8))) {
        reader
            .lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(modules::add);
      }
    }

    return modules;

  }

}
