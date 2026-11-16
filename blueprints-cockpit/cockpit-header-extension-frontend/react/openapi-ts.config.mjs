// How `npm run generate:client` writes the API client: @hey-api/openapi-ts reads the OpenAPI file,
// which scripts/fetch-openapi.mjs copied to build/openapi, and writes TypeScript to
// src/generated/loan-approval. It runs on Node only, so the build needs no Java and downloads
// nothing.
import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'build/openapi/loan-approval.yaml',
  output: 'src/generated/loan-approval',
  plugins: [
    // An answer which is not a success throws, see the error interceptor in src/shared/api.ts.
    { name: '@hey-api/client-fetch', throwOnError: true },
    '@hey-api/typescript',
    {
      name: '@hey-api/sdk',
      // One class per tag, named like the Java client: the tag `start-form` becomes StartFormApi.
      operations: { strategy: 'byTags', containerName: '{{name}}Api', methods: 'instance' },
      // A method takes one object with all parameters, the body under the name of its schema.
      paramsStructure: 'flat',
      // A method returns the data of the answer, not the answer.
      responseStyle: 'data',
    },
  ],
});
