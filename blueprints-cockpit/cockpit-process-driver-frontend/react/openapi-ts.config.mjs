// How `npm run generate:client` writes the API clients: @hey-api/openapi-ts reads the three OpenAPI
// files, which scripts/fetch-openapi.mjs copied to build/openapi, and writes TypeScript to
// src/generated/<name of the file>. It runs on Node only, so the build needs no Java and downloads
// nothing.
//
//   loan-approval      the API of the use case, which the forms call
//   process-driver     the API of the process driver, which only the dev shell calls
//   official-gui-api   the official API of the cockpit, which only the dev shell calls
import { defineConfig } from '@hey-api/openapi-ts';

const client = (name, baseUrl) => ({
  input: `build/openapi/${name}.yaml`,
  output: `src/generated/${name}`,
  plugins: [
    {
      name: '@hey-api/client-fetch',
      // An answer which is not a success throws, see the error interceptor in src/shared/response-error.ts.
      throwOnError: true,
      // The index of the generated files exports the client as well, so src/shared/api.ts imports
      // all of it from '@generated', src/shared/process-driver-api.ts from
      // '@generated-process-driver' and src/shared/official-gui-api.ts from
      // '@generated-official-gui-api'.
      exportFromIndex: true,
      // Where the browser sends the calls, see below. The browser talks to one origin only.
      baseUrl,
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

export default defineConfig([
  // Both files of the backend name the application as their server, http://localhost:8080, and
  // every path starts with /loan-approval/api/. In the browser the cockpit forwards
  // /wm/loan-approval/ to the workflow-module-uri of the module, which ends with /loan-approval.
  // The dev shell does the same. So the client puts /wm in front of each path and calls
  // /wm/loan-approval/api/...
  client('loan-approval', '/wm'),
  client('process-driver', '/wm'),
  // The file of the cockpit names /gui/api/v1 as its server. The dev shell simulator answers the
  // cockpit below a prefix of its own, /official-api, and the dev shell forwards that prefix to it.
  client('official-gui-api', '/official-api/gui/api/v1'),
]);
