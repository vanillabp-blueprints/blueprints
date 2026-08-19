package blueprint.api;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.util.ClassUtils;

/**
 * Names the beans of a workflow module {@code <module-id>_<SimpleName>}, so two modules can
 * bring classes of the same name without either of them knowing about the other.
 *
 * <p>
 * It is needed because the reference structure gives every use case a class called
 * {@code Service} and one called {@code ApiController}, and Spring's default names a bean
 * after its class alone. The second module to be registered then ends the boot with
 * <em>conflicts with existing, non-compatible bean definition</em> - a message about a name
 * nobody wrote.
 * </p>
 *
 * <p>
 * A name a developer gave explicitly wins, which is what the superclass does; only the
 * derived names get the prefix. The module ID is what a module is called everywhere else,
 * so a bean name now says which module a bean belongs to, in a stack trace as well as in an
 * actuator listing.
 * </p>
 *
 * <p>
 * A technical helper, with no opinion about any business case, which is why it may live in
 * the JAR both modules depend on. Each module contributes the one line saying which module
 * it is, because Spring instantiates a generator without arguments.
 * </p>
 */
public abstract class WorkflowModuleBeanNameGenerator extends AnnotationBeanNameGenerator {

  private final String workflowModuleId;

  /**
   * @param workflowModuleId The ID of the workflow module whose beans are named.
   */
  protected WorkflowModuleBeanNameGenerator(
      final String workflowModuleId) {

    this.workflowModuleId = workflowModuleId;

  }

  @Override
  protected String buildDefaultBeanName(
      final BeanDefinition definition,
      final BeanDefinitionRegistry registry) {

    return workflowModuleId
        + "_"
        + ClassUtils.getShortName(definition.getBeanClassName());

  }

}
