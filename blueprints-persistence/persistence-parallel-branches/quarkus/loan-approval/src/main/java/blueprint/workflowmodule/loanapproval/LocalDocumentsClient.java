package blueprint.workflowmodule.loanapproval;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * A stand-in for the document service, so the blueprint runs without one. Replace it with
 * the real client; nothing else in this workflow module changes.
 */
@Slf4j
@ApplicationScoped
public class LocalDocumentsClient implements DocumentsClient {

  @Override
  public int collect(
      final String loanRequestId) {

    log.info("Collecting the documents of loan approval '{}'", loanRequestId);

    return 3;

  }

}
