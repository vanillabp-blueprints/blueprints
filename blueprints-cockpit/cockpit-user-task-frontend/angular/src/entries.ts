// The entry point of the Angular CLI build, see angular.json. The cockpit never loads this file.
// Each import() below makes the Angular CLI write the entry as an ES module of its own, with the
// hash of its content in the file name, the way it writes a lazy loaded route.
// scripts/write-manifest.mjs then names these files in bc-manifest.json. Angular itself goes into
// a chunk which the two forms share, so the browser loads Angular once for this module. The data
// entries never import it. `npm run check:entries` proves that afterwards.
//
// A new entry needs a line here and a line in scripts/write-manifest.mjs.

export const entries = {
  UserTaskForm: () => import('./data/user-task-form.ts'),
  UserTaskList: () => import('./data/user-task-list.ts'),
  AssessRiskForm: () => import('./forms/assess-risk-form.ts'),
  CheckDocumentsForm: () => import('./forms/check-documents-form.ts'),
};
