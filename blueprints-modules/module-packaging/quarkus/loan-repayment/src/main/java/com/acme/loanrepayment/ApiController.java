package com.acme.loanrepayment;

import java.util.UUID;

import com.acme.loanrepayment.api.LoanRepaymentApi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of the second use case, GET requests only, so the process can be walked through
 * in a browser.
 *
 * <p>
 * The path starts with the module's own id, as the other module's does with its own. Two
 * modules on one HTTP port is the same kind of collision as two modules on one classpath,
 * and it is avoided the same way: by giving each of them a namespace of its own.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanRepaymentApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-repayment.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@ApplicationScoped
public class ApiController implements LoanRepaymentApi {

  @Inject
  Service loanRepayment;

  /**
   * Starts a repayment.
   *
   * @param customerId The customer paying back.
   * @param amount     The amount owed.
   * @return The id of the repayment started.
   */
  @Override
  public String start(
      final String customerId,
      final Integer amount) {

    final var repaymentId = UUID.randomUUID().toString();

    loanRepayment.initiate(repaymentId, customerId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-repayment/{}",
        repaymentId);

    return repaymentId;

  }

  /**
   * Shows what the process did.
   *
   * @param repaymentId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String repaymentId) {

    return loanRepayment
        .get(repaymentId)
        .map(Object::toString)
        .orElse("unknown repayment '"
            + repaymentId
            + "'");

  }

}
