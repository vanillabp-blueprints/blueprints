package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.loanapproval.Service;

/**
 * The workflow module wires itself. An application adds the module as a Maven dependency, and
 * that is all it has to do.
 *
 * <p>
 * A Spring Boot application scans its own package and the packages below it. An application
 * may run several workflow modules, and its package is then above none of them. So the module
 * says once what it brings:
 * </p>
 *
 * <p>
 * The class is in the sub-package {@code config}, next to the other configuration of the module.
 * So every scan names the package of the module itself, by {@link Service}. Without it, a scan
 * would only search {@code config}.
 * </p>
 *
 * <ul>
 * <li>{@code @ComponentScan} finds the beans of the module: the service, the workflow classes
 * and the controllers. Every workflow module of the reference structure has classes called
 * {@code Service}, {@code Workflow} and {@code ApiController}. With the short names as bean
 * names, the second module in one application ends the boot. So the beans get their fully
 * qualified class name as name. The scan leaves out the auto-configurations of the module.
 * Spring Boot loads them itself, and loading them twice would register their beans twice.</li>
 * <li>{@code @EntityScan} and {@code @EnableJpaRepositories} find the aggregate of the module
 * and the repository which reads it, in the package of the module and nowhere else. The
 * repository gets its fully qualified name as well. Spring Boot then looks for entities and
 * repositories only where such annotations say. So an application with entities of its own
 * names its package the same way. Adding the package of the module to the packages of the
 * application instead would scan it twice, as soon as the application is above the module.</li>
 * </ul>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which Spring Boot reads from every JAR on the class path. The files of the user interface
 * need nothing here, if they come from the UI jar. Spring Boot serves the files below
 * {@code META-INF/resources/} of every JAR. If they come from an nginx,
 * {@link UserInterfaceRouteAutoConfiguration} forwards them.
 * </p>
 */
@AutoConfiguration
@EntityScan(basePackageClasses = Service.class)
@EnableJpaRepositories(
    basePackageClasses = Service.class,
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ComponentScan(
    basePackageClasses = Service.class,
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
    excludeFilters = @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
public class LoanApprovalAutoConfiguration {

  /**
   * The id of this workflow module, as in {@code META-INF/workflow-module}. It is the first
   * part of the paths of the module and of the keys the module reads itself.
   */
  public static final String WORKFLOW_MODULE_ID = "loan-approval";

}
