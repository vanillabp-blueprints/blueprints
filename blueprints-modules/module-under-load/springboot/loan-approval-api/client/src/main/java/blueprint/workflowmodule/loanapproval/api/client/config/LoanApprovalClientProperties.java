package blueprint.workflowmodule.loanapproval.api.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.cockpit.support.rest.adapter.Client;

/**
 * Where the application under load is and how the client talks to it, read from {@code loan-
 * approval-api.*}. The keys are the standard keys of a REST client of the Business Cockpit support:
 * {@code base-url}, {@code connect-timeout}, {@code read-timeout}, {@code retry.*}, {@code
 * authentication.*} and more. The README of the support lists them all.
 */
@ConfigurationProperties(prefix = "loan-approval-api")
public class LoanApprovalClientProperties extends Client {

  /**
   * Creates the settings. Under load the application gets slow, and a slow answer is part of the
   * result. So an answer may take thirty seconds. Only a call which takes longer counts as failed.
   */
  public LoanApprovalClientProperties() {

    setConnectTimeout(1500);
    setReadTimeout(30000);

  }

}
