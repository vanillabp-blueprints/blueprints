// The types of src/bc-build.js. scripts/write-bc-build.mjs writes that file for every build, so
// only its types are checked in.

/** The id the module registers with at the cockpit, from `config.workflowModuleId` in package.json. */
export declare const WORKFLOW_MODULE_ID: string;

/** The time of the build in milliseconds, in base 36. New for every build, see createElementNames. */
export declare const BUILD_ID: string;
