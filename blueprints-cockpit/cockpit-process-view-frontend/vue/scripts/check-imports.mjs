// Fails the build if a source file imports across a directory with '../'. Such a path breaks
// when a file moves, and it does not say what it imports. The path aliases of tsconfig.json say
// it: '@loan-approval', '@shared', '@gui-client' and '@generated'. An import from the same
// directory, './', stays allowed. The generated client in src/generated is left out, because
// this module does not write it.
//
// It is a script and not an ESLint rule, because this project has no ESLint. `npm run build`
// runs it first.

import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const sources = join(projectDir, 'src');
const generated = join(sources, 'generated');

// An import, an export ... from, or a dynamic import() whose path starts with '../'.
const IMPORT_FROM_A_PARENT = /(?:\bfrom\s*|\bimport\s*\(?\s*)['"]\.\.\//;

function filesBelow(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return path === generated ? [] : filesBelow(path);
    return /\.(ts|tsx|vue)$/.test(entry.name) ? [path] : [];
  });
}

const found = filesBelow(sources).flatMap((file) => readFileSync(file, 'utf8')
  .split('\n')
  .map((line, index) => ({ file, line, number: index + 1 }))
  .filter(({ line }) => IMPORT_FROM_A_PARENT.test(line)));

if (found.length > 0) {
  for (const { file, line, number } of found) console.error(`${relative(projectDir, file)}:${number}: ${line.trim()}`);
  console.error(`\n${found.length} import(s) with '../'. Import through a path alias of tsconfig.json instead.`);
  process.exit(1);
}
console.log('check-imports: no import with ../');
