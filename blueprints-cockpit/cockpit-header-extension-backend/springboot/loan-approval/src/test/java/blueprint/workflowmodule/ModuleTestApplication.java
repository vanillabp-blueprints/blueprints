package blueprint.workflowmodule;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * A workflow module is a JAR and cannot be started on its own, so testing it means bringing
 * a minimal application along. This is that application. It scans nothing.
 *
 * <p>
 * The module brings an auto-configuration, and that is how an application gets its beans.
 * Booting it here through a component scan instead would test a wiring nobody uses. It would
 * also register every bean twice, once under the name the scan derives and once under the
 * fully qualified name the module's auto-configuration gives it.
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
