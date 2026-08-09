package blueprint.workflowmodule;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A workflow module is a JAR and cannot be started on its own, so testing it means
 * bringing a minimal application along. This is that application: it exists only in the
 * test sources and does nothing but boot the module together with a database and a BPMS
 * adapter.
 */
@SpringBootApplication
public class TestApplication {
}
