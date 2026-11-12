package blueprint.workflowmodule.loanapproval.config;

import static blueprint.workflowmodule.loanapproval.config.LoanApprovalAutoConfiguration.WORKFLOW_MODULE_ID;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the files of the user interface to the nginx which serves them, if the application
 * says where that nginx is.
 *
 * <p>
 * The cockpit loads the user interface of the workflow module from
 * {@code /wm/loan-approval/bc-manifest.json} and forwards that to
 * {@code /loan-approval/bc-manifest.json} of the application. The same holds for the files the
 * manifest names. An application without the UI jar has no such files. So this route passes
 * every such request on to the nginx, with the path unchanged. The nginx serves the
 * micro-frontends below the same path.
 * </p>
 *
 * <p>
 * The API of the module is below {@code /loan-approval/api/} and stays in the application. The
 * route leaves it out on purpose. Spring MVC asks a route like this one before it asks the
 * controllers, so a route for {@code /loan-approval/**} alone would send the API to the nginx as
 * well.
 * </p>
 *
 * <p>
 * The route is only there if two things are true. The gateway is on the class path, because
 * the application added its starter. And the application set {@value #ADDRESS}. Without the
 * address, the files come from the UI jar. With the address, Spring MVC asks the route before
 * it looks into the jars, so the nginx answers even if the UI jar is there as well.
 * The key is below the id of the module, like every key the module reads itself. It is not
 * below {@code vanillabp}, because VanillaBP and the cockpit adapter own that namespace and
 * refuse keys they do not read.
 * </p>
 *
 * <p>
 * Nothing in this class is specific to the loan approval but its id. Every workflow module with
 * a user interface needs the same route. The library {@code support} of the Business Cockpit is
 * the place for it, but it does not have it yet.
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(GatewayRouterFunctions.class)
@ConditionalOnProperty(UserInterfaceRouteAutoConfiguration.ADDRESS)
public class UserInterfaceRouteAutoConfiguration {

  /** The key of the address of the nginx. Only its scheme, host and port count. */
  public static final String ADDRESS = WORKFLOW_MODULE_ID
      + ".user-interface.address";

  /** Where the files of the workflow module are, below the path of its address. */
  private static final String USER_INTERFACE_FILES = "/"
      + WORKFLOW_MODULE_ID
      + "/**";

  /** The API of the workflow module, which the application answers itself. */
  private static final String MODULE_API = "/"
      + WORKFLOW_MODULE_ID
      + "/api/**";

  @Bean(WORKFLOW_MODULE_ID
      + "-user-interface")
  public RouterFunction<ServerResponse> userInterface(
      @Value("${"
          + ADDRESS
          + "}") final URI nginx) {

    return GatewayRouterFunctions
        .route(WORKFLOW_MODULE_ID
            + "-user-interface")
        .route(GET(USER_INTERFACE_FILES).and(path(MODULE_API).negate()), HandlerFunctions.http())
        .before(BeforeFilterFunctions.uri(nginx))
        .build();

  }

}
