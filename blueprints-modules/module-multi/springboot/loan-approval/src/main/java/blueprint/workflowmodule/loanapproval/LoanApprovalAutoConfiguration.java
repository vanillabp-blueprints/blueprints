package blueprint.workflowmodule.loanapproval;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;

/**
 * The workflow module wires itself. This class is what turns a JAR into something an
 * application can simply depend on.
 *
 * <p>
 * A Spring Boot application scans its own package and everything below it. This module is
 * not below it, deliberately: it is a JAR somebody else may consume, and in a real project
 * it comes from a registry rather than from the directory next door. So the module says
 * what it contributes, once, and every application using it is spared the knowledge:
 * </p>
 *
 * <ul>
 * <li>{@code @ComponentScan} for its beans - the service, the wiring classes, the API. It
 * names them by their fully qualified class name, which is not decoration: every use case
 * of the reference structure has a class called {@code Service} and one called
 * {@code ApiController}, so two modules in one application would otherwise fight over the
 * bean name {@code apiController} and the second one to be scanned ends the boot,</li>
 * <li>{@code @EntityScan} and {@code @EnableJpaRepositories} for its aggregate and the
 * repository reading it,</li>
 * <li>{@code @EnableConfigurationProperties} for the properties class fed from
 * {@code loan-approval/loan-approval.yaml}.</li>
 * </ul>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which Spring Boot reads from every JAR on the classpath. Without that file the class is
 * just a class, and the symptom is the one the README describes: the application starts
 * until VanillaBP looks for the bean behind a {@code @WorkflowTask} method.
 * </p>
 *
 * <p>
 * What VanillaBP finds on its own is only the BPMN wiring: it scans the classpath for
 * {@code @WorkflowService} classes itself, so the process is known even when the bean
 * behind it is not. That is why the failure names a missing bean rather than a missing
 * module.
 * </p>
 */
@AutoConfiguration
@ComponentScan
@EntityScan
@EnableJpaRepositories(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class LoanApprovalAutoConfiguration {
}
