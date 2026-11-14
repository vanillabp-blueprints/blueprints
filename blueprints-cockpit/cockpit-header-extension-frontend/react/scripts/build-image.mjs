// Builds the nginx image with the micro-frontends, for an application which takes them from an
// nginx instead of from the UI jar. The image serves the same paths as the jar:
// /loan-approval/bc-manifest.json and the files it names.
//
// With --skip-without-docker, a machine without Docker skips the image and says so. `npm run
// build` uses it, because many build agents have Node but no Docker. `npm run build:image` does
// not, so there a missing Docker is an error.

import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const { version, config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const image = `${process.env.IMAGE_NAME ?? config.image}:${version}`;

const docker = spawnSync('docker', ['version', '--format', '{{.Server.Version}}'], { encoding: 'utf8' });
if (docker.status !== 0) {
  if (process.argv.includes('--skip-without-docker')) {
    console.warn('build-image: skipped, because Docker is not running here. `npm run build:image` builds the image where it is.');
    process.exit(0);
  }
  console.error(`build-image: Docker is not running here. ${docker.stderr ?? ''}`.trim());
  process.exit(1);
}

const result = spawnSync('docker', ['build', '--file', 'docker/Dockerfile', '--tag', image, '--build-arg', `WORKFLOW_MODULE_ID=${config.workflowModuleId}`, '.'], {
  cwd: projectDir,
  stdio: 'inherit',
});
if (result.status !== 0) process.exit(result.status ?? 1);
console.log(`build-image: ${image}`);
