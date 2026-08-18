package blueprint.workflowmodule.loanrepayment.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of this workflow module, fed from
 * {@code loan-repayment/loan-repayment.yaml} inside this JAR. Each module brings its own
 * file, named after its own module id, so two modules cannot overwrite each other's values.
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-modules-in-Quarkus#configuration">Configuration
 *      of workflow modules</a>
 */
@ConfigMapping(prefix = "loan-repayment")
public interface LoanRepaymentProperties {

  /**
   * The largest number of installments a repayment may be split into.
   *
   * @return The maximum.
   */
  @WithDefault("12")
  int maxInstallments();

}
