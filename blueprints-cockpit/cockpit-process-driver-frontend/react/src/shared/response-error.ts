// An answer which is not a success. The generated clients throw only the body of such an answer,
// without its status. So without this class a form could not tell 409 from any other failure.

export class ResponseError extends Error {
  readonly response: Response;
  /** What the answer said, for example the reason of a 409 as text. */
  readonly body: unknown;

  constructor(response: Response, body: unknown) {
    super(`${response.status} ${response.statusText}`);
    this.name = 'ResponseError';
    this.response = response;
    this.body = body;
  }
}

/**
 * Turns every answer of a client which is not a success into a ResponseError. A network error
 * has no answer and stays what fetch threw.
 */
export function throwResponseErrors(client: GeneratedClient) {
  client.interceptors.error.use((error, response) => (response && !response.ok ? new ResponseError(response, error) : error));
}

/** The part of a generated client this file needs. Each generated client has a type of its own. */
interface GeneratedClient {
  interceptors: { error: { use(interceptor: (error: unknown, response: Response | undefined) => unknown): unknown } };
}
