package blueprint.workflowmodule;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * A workflow module is a JAR and cannot be started on its own, so testing it means bringing
 * a minimal application along. This is that application, and it is deliberately smaller than
 * the one the other blueprints use: it scans nothing.
 *
 * <p>
 * A module which publishes itself brings an auto-configuration, and that is how a consumer
 * gets its beans. Booting it here through a component scan instead would test a wiring
 * nobody uses - and it would register every bean twice, once under the name the scan derives
 * and once under the name the module's own generator gives it.
 * </p>
 *
 * <p>
 * {@code @EnableAutoConfiguration} is what makes the module's auto-configuration apply,
 * together with the database, the transaction manager and the BPMS adapter the test needs.
 * </p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ModuleTestApplication {
}
