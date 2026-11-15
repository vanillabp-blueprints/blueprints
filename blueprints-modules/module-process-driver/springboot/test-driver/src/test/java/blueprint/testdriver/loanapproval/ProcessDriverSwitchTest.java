package blueprint.testdriver.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import blueprint.testdriver.loanapproval.config.ProcessDriverAutoConfiguration;

/**
 * The driver is off unless somebody switches it on. An application which has this module on its
 * classpath by mistake shows no endpoint of it.
 */
public class ProcessDriverSwitchTest {

  private final ApplicationContextRunner application = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ProcessDriverAutoConfiguration.class));

  @Test
  public void theDriverIsOffByDefault() {

    application.run(context -> assertThat(context)
        .doesNotHaveBean(ProcessDriver.class)
        .doesNotHaveBean(ProcessDriverController.class));

  }

  @Test
  public void theDriverIsOffWhenTheSwitchSaysSo() {

    application
        .withPropertyValues("process-driver.enabled=false")
        .run(context -> assertThat(context)
            .doesNotHaveBean(ProcessDriver.class)
            .doesNotHaveBean(ProcessDriverController.class));

  }

}
