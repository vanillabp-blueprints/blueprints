package blueprint.workflowmodule.bundle;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Wires the workflow module {@code loan-approval} into any application which has this bundle
 * on its class path. This class is what makes the bundle one dependency and nothing else.
 *
 * <p>
 * An application scans its own package and the packages below it. An application with
 * several workflow modules has a package of its own, and it does not reach the package of this
 * module. So the bundle says what the module brings, once:
 * </p>
 *
 * <ul>
 * <li>{@code @ComponentScan} finds the beans of the module: the service, the workflow classes
 * and the controllers. Every workflow module of the reference structure has classes called
 * {@code Service}, {@code Workflow} and {@code ApiController}. With the short names as bean
 * names, the second module in one application ends the boot. So the beans get their fully
 * qualified class name as name.</li>
 * <li>{@code @AutoConfigurationPackage} adds the package of the module to the packages Spring
 * Boot looks for entities and Spring Data repositories in. The package of the application
 * stays in that list, so its own entities are still found. {@code @EntityScan} would replace
 * the list instead. This only works if Spring Boot reads the list after this class, which is
 * what {@code before} says.</li>
 * </ul>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which Spring Boot reads from every JAR on the class path. The user interface needs nothing
 * here. Spring Boot serves the files below {@code META-INF/resources/} of every JAR.
 * </p>
 */
@AutoConfiguration(before = {
    HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class
})
@AutoConfigurationPackage(basePackages = "blueprint.workflowmodule.loanapproval")
@ComponentScan(
    basePackages = "blueprint.workflowmodule.loanapproval",
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class LoanApprovalBundleAutoConfiguration {
}
