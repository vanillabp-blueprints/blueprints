package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;
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
 * The test plays it by pointing Liquibase at a changelog which applies everything except
 * VanillaBP's part, which is the realistic mistake: a changelog that forgot one include line.
 * The engine is left to create its own tables here, because a missing engine schema would end
 * the boot earlier and with the engine's message, and what is under test is VanillaBP's.
 * </p>
 */
public class MissingTableIT {

  @Test
  public void aMissingTableEndsTheBootAndSaysWhatToDo() {

    // Command line arguments, not SpringApplicationBuilder#properties: those are default
    // properties and rank BELOW the profile file, which names the changelog of the engine.
    final var arguments = new ArrayList<String>();
    arguments.add("--blueprint.schema.changelog=classpath:db/changelog-without-vanillabp.xml");
    // a database of its own, so nothing another test left behind can be found here
    arguments.add("--spring.datasource.url=jdbc:h2:mem:missing-table;DB_CLOSE_DELAY=-1");
    arguments.add("--server.port=0");
    if (ClassUtils.isPresent("org.camunda.bpm.engine.ProcessEngine", getClass().getClassLoader())) {
      // the engine creates its own tables here: a missing engine schema would end the boot
      // earlier and with the engine's message, and VanillaBP's is what is under test
      arguments.add("--vanillabp.adapters.camunda7.database-schema-update=true");
    }

    assertThatThrownBy(
        () -> new SpringApplicationBuilder(Application.class)
            .run(arguments.toArray(String[]::new)))
        .satisfies(thrown -> {
          final var message = NestedExceptionUtils
              .getMostSpecificCause(thrown)
              .getMessage();
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

}
