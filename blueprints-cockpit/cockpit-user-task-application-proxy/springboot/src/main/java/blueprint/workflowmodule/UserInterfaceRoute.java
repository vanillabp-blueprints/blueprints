package blueprint.workflowmodule;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the files of the user interface to the nginx which serves them.
 *
 * <p>
 * The cockpit loads the user interface of the workflow module from
 * {@code /wm/loan-approval/bc-manifest.json} and forwards that to
 * {@code /loan-approval/bc-manifest.json} here. The same holds for the files the manifest names.
 * This application has no UI jar, so it passes every such request on to the nginx, with the
 * path unchanged. The nginx serves the micro-frontends below the same path.
 * </p>
 *
 * <p>
 * The API of the module is below {@code /loan-approval/api/} and stays here. The route leaves
 * it out on purpose: Spring MVC asks a route like this one before it asks the controllers, so
 * a route for {@code /loan-approval/**} alone would send the API to the nginx as well.
 * </p>
 *
 * <p>
 * The browser still sees one origin, the cockpit's. And the security of this application
 * covers the files of the user interface, because they pass through it.
 * </p>
 */
@Configuration
public class UserInterfaceRoute {

  /** Where the files of the workflow module are, below the path of its address. */
  private static final String USER_INTERFACE_FILES = "/loan-approval/**";

  /** The API of the workflow module, which this application answers itself. */
  private static final String MODULE_API = "/loan-approval/api/**";

  @Bean
  public RouterFunction<ServerResponse> userInterface(
      @Value("${user-interface.address}") final URI nginx) {

    return GatewayRouterFunctions
        .route("loan-approval-user-interface")
        .route(GET(USER_INTERFACE_FILES).and(path(MODULE_API).negate()), HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri(nginx))
        .build();

  }

}
