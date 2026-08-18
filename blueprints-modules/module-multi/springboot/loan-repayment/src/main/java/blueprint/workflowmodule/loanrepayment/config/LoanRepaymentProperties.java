package blueprint.workflowmodule.loanrepayment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration of this workflow module, fed from
 * {@code loan-repayment/loan-repayment.yaml} inside this JAR. Each module brings its own
 * file, named after its own module id, so two modules cannot overwrite each other's values.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Spring-Boot#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigurationProperties(prefix = "loan-repayment")
@Data
public class LoanRepaymentProperties {

  /** The largest number of installments a repayment may be split into. */
  private int maxInstallments = 12;

}
