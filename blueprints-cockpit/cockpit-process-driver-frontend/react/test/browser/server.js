// A small web server for the browser tests. It serves the test page, the bundled host, and the
// built micro-frontends below /wm/loan-approval/, where a cockpit serves them. The module API
// below /wm/loan-approval/api/ is not served here. Each test answers it in the browser, see
// mockApi in user-task-forms.test.js.

import { readFileSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, sep } from 'node:path';

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json' };

/**
 * @param {{ files: Record<string, string>, folders: Record<string, string> }} options
 *   files: path -> file; folders: path prefix -> folder
 */
export async function startServer({ files, folders }) {
  const server = createServer((request, response) => {
    const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    let file = files[path];
    if (!file) {
      const prefix = Object.keys(folders).find((candidate) => path.startsWith(candidate));
      if (prefix) {
        const candidate = normalize(join(folders[prefix], path.slice(prefix.length)));
        if (candidate.startsWith(folders[prefix] + sep)) file = candidate;
      }
    }
    try {
      if (!file || !statSync(file).isFile()) throw new Error('not found');
      response.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' }).end(readFileSync(file));
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = /** @type {import('node:net').AddressInfo} */ (server.address());
  return { url: `http://127.0.0.1:${port}`, close: () => new Promise((resolve) => server.close(resolve)) };
}
