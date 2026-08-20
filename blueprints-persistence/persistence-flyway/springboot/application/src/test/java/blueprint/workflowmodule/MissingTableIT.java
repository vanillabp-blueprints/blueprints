package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.ClassUtils;

/**
 * The other half of handing the schema over: a deployment which forgot to apply the
 * migration must not look healthy.
 *
 * <p>
 * With {@code vanillabp.outbox.create-schema} off, VanillaBP checks its tables while the
 * application starts and ends the boot naming the table, the property which would have
 * created it and the artifact carrying the statements. Without that check the application
 * would come up, serve requests and fail at the first workflow, hours after the deployment
 * and looking like a bug of the application.
 * </p>
 *
 * <p>
 * Two owners can be forgotten, so both are played through, each by pointing one Flyway instance
 * at a location without migrations. That is the realistic mistake: a deployment which applied
 * everything except one part. The engine is left to create its own tables here, because a missing
 * engine schema would end the boot earlier and with the engine's message, and what is under test
 * is VanillaBP's.
 * </p>
 *
 * <p>
 * The outbox table of the outbox library is the more interesting of the two: its statements are
 * the one piece of schema this application writes down itself, so it is the one a deployment can
 * forget without anything else noticing.
 * </p>
 */
public class MissingTableIT {

  @Test
  public void aMissingVanillaBpTableEndsTheBootAndSaysWhatToDo() {

    assertThatThrownBy(
        () -> bootWith("--blueprint.schema.vanillabp-location=classpath:db/no-migrations"))
        .satisfies(thrown -> {
          final var message = messageOf(thrown);
          assertThat(message)
              .describedAs("The message names the table which is missing")
              .contains("VANILLABP_TASK_DELIVERY");
          assertThat(message)
              .describedAs("and the property which would have created it")
              .contains("vanillabp.outbox.create-schema");
          assertThat(message)
              .describedAs("and the artifact to apply")
              .contains("vanillabp-schema");
        });

  }

  @Test
  public void aMissingOutboxTableOfTheOutboxLibraryEndsTheBootAsWell() {

    // the application's own instance is the one carrying gruelbox's migration
    assertThatThrownBy(
        () -> bootWith("--blueprint.schema.application-locations=classpath:db/no-migrations"))
        .satisfies(thrown -> {
          final var message = messageOf(thrown);
          assertThat(message)
              .describedAs("The message names the table which is missing")
              .contains("TXNO_OUTBOX");
          assertThat(message)
              .describedAs("and says that this one is not VanillaBP's")
              .contains("vanillabp-schema");
          assertThat(message)
              .describedAs("and where the statements for it come from")
              .contains("writeSchema");
        });

  }

  /**
   * @param location The argument pointing one owner's migrations at an empty location
   */
  private void bootWith(
      final String location) {

    // Command line arguments, not SpringApplicationBuilder#properties: those are default
    // properties and rank BELOW the profile file, which names the locations of the engine.
    final var arguments = new ArrayList<String>();
    arguments.add(location);
    // a database of its own per case, so nothing another test left behind can be found here
    arguments.add("--spring.datasource.url=jdbc:h2:mem:missing-table-"
        + location.hashCode()
        + ";DB_CLOSE_DELAY=-1");
    arguments.add("--server.port=0");
    // No web server: the application under test is booted for its startup checks only, and a
    // servlet container failing to start its filters because the context is already broken
    // would hide the message this test is about.
    arguments.add("--spring.main.web-application-type=none");
    if (ClassUtils.isPresent("org.camunda.bpm.engine.ProcessEngine", getClass().getClassLoader())) {
      // the engine creates its own tables here: a missing engine schema would end the boot
      // earlier and with the engine's message, and VanillaBP's is what is under test
      arguments.add("--vanillabp.adapters.camunda7.database-schema-update=true");
    }

    new SpringApplicationBuilder(Application.class)
        .run(arguments.toArray(String[]::new));

  }

  /**
   * @param thrown What starting the application threw
   * @return Every message of the exception chain, so an assertion does not depend on which
   *         layer wrapped the failure last
   */
  private static String messageOf(
      final Throwable thrown) {

    final var messages = new StringBuilder();
    for (var cause = thrown; cause != null; cause = cause.getCause()) {
      messages
          .append(cause.getMessage())
          .append('\n');
    }
    return messages.toString();

  }

}
