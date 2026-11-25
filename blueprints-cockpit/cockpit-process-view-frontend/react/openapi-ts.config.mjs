// How `npm run generate:client` writes the API client: @hey-api/openapi-ts reads the OpenAPI file,
// which scripts/fetch-openapi.mjs copied to build/openapi, and writes TypeScript to
// src/generated/loan-approval. It runs on Node only, so the build needs no Java and downloads
// nothing.
import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'build/openapi/loan-approval.yaml',
  output: 'src/generated/loan-approval',
  plugins: [
    {
      name: '@hey-api/client-fetch',
      // An answer which is not a success throws, see the error interceptor in src/shared/api.ts.
      throwOnError: true,
      // The index of the generated files exports the client as well, so src/shared/api.ts imports
      // all of it from '@generated'.
      exportFromIndex: true,
      // The OpenAPI file of the backend names the application as its server,
      // http://localhost:8080, and every path starts with /loan-approval/api/. So a person can type
      // each URL into the browser. In the cockpit, /wm/loan-approval/ goes to the
      // workflow-module-uri of the module, which ends with /loan-approval. So the client puts /wm
      // in front of each path and calls /wm/loan-approval/api/...
      baseUrl: '/wm',
    },
    '@hey-api/typescript',
    {
      name: '@hey-api/sdk',
      // One class per tag, named like the Java client: the tag `loan-approval` becomes LoanApprovalApi.
      operations: { strategy: 'byTags', containerName: '{{name}}Api', methods: 'instance' },
      // A method takes one object with all parameters.
      paramsStructure: 'flat',
      // A method returns the data of the answer, not the answer.
      responseStyle: 'data',
    },
  ],
});
