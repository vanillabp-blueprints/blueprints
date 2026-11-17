// `npm run start` builds the micro-frontends and the dev shell and serves them at
// http://localhost:4200. It builds again when a file below src/ or dev-shell/src/ changes. Reload
// the page to see the change.
//
// The browser talks to this server only. It answers from the build, and it forwards the rest:
//
//   /                                    dist/dev-shell
//   /wm/loan-approval/api/...  the application, as /loan-approval/api/...
//   /wm/loan-approval/...      dist/micro-frontends, as the UI jar would serve them
//   /official-api/...          the simulated cockpit
//
// A real cockpit forwards /wm/loan-approval/ to the workflow-module-uri of the module, which ends
// with /loan-approval. So the forms call the same paths here as in production, and the process
// driver is below /loan-approval/api/ as well.
//
//   DEV_SHELL_PORT     the port of the dev shell, 4200 by default
//   APPLICATION_URL    the application, http://localhost:8080 by default
//   COCKPIT_URL        the simulated cockpit, http://localhost:8079 by default

import { spawnSync } from 'node:child_process';
import { readFileSync, statSync, watch } from 'node:fs';
import { createServer, request as forward } from 'node:http';
import { extname, join, normalize, resolve, sep } from 'node:path';

const projectDir = resolve(import.meta.dirname, '..');
const { config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const modulePath = `/wm/${config.workflowModuleId}/`;
const port = Number(process.env.DEV_SHELL_PORT ?? 4200);
const applicationUrl = new URL(process.env.APPLICATION_URL ?? 'http://localhost:8080');
const cockpitUrl = new URL(process.env.COCKPIT_URL ?? 'http://localhost:8079');

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.map': 'application/json' };

/** The paths which go to the application or to the cockpit, and how each is rewritten. */
const FORWARDS = [
  { prefix: `${modulePath}api/`, to: applicationUrl, strip: '/wm'.length },
  { prefix: '/official-api/', to: cockpitUrl, strip: 0 },
];

/** The folders this server answers from. */
const FOLDERS = [
  { prefix: modulePath, folder: join(projectDir, 'dist', 'micro-frontends') },
  { prefix: '/', folder: join(projectDir, 'dist', 'dev-shell') },
];

function rebuild() {
  for (const script of ['build-micro-frontends.mjs', 'build-dev-shell.mjs']) {
    const result = spawnSync(process.execPath, [join(projectDir, 'scripts', script)], { cwd: projectDir, stdio: 'inherit' });
    if (result.status !== 0) return false;
  }
  return true;
}

function serveFile(path, response) {
  const { prefix, folder } = FOLDERS.find((candidate) => path.startsWith(candidate.prefix));
  const file = normalize(join(folder, path === '/' ? 'index.html' : path.slice(prefix.length)));
  try {
    if (!file.startsWith(folder + sep) || !statSync(file).isFile()) throw new Error('not found');
    response.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-cache' }).end(readFileSync(file));
  } catch {
    response.writeHead(404).end();
  }
}

function forwardTo({ to, strip }, request, response) {
  const outgoing = forward(
    { protocol: to.protocol, hostname: to.hostname, port: to.port, path: request.url.slice(strip), method: request.method, headers: { ...request.headers, host: to.host } },
    (answer) => {
      response.writeHead(answer.statusCode ?? 502, answer.headers);
      answer.pipe(response);
    },
  );
  // Nothing listens there. The dev shell turns 502 into a message which says what to start.
  outgoing.on('error', () => {
    if (!response.headersSent) response.writeHead(502, { 'content-type': 'text/plain' });
    response.end(`Nothing answers at ${to.origin}.`);
  });
  request.pipe(outgoing);
}

if (!rebuild()) {
  console.error('npm run start: the build failed. Run `npm run build` once, it also generates the API clients.');
  process.exit(1);
}

let timer;
for (const folder of ['src', join('dev-shell', 'src')]) {
  watch(join(projectDir, folder), { recursive: true }, (_, file) => {
    if (String(file).startsWith('generated')) return;
    clearTimeout(timer);
    timer = setTimeout(rebuild, 200);
  });
}

createServer((request, response) => {
  const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  const target = FORWARDS.find(({ prefix }) => path.startsWith(prefix));
  if (target) forwardTo(target, request, response);
  else serveFile(path, response);
}).listen(port, () => {
  console.log(`The dev shell -> http://localhost:${port}/#/process-driver`);
  console.log(`  the application at ${applicationUrl.origin}, the simulated cockpit at ${cockpitUrl.origin}`);
});
