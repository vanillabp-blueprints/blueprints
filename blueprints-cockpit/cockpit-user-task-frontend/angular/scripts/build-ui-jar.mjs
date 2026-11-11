// Builds the UI jar io.vanillabp.blueprint:cockpit-user-task-ui and its pom into dist/.
//
// A jar is a zip file with META-INF/MANIFEST.MF. This one holds no class, only the
// micro-frontends below META-INF/resources/loan-approval/. A Java application with this jar on its
// class path serves them at /loan-approval/, for example /loan-approval/bc-manifest.json. So no
// Maven and no JDK are needed to build it.
//
// The React, the Angular and the Vue frontend of this blueprint all build a jar with the same
// coordinates. The application which takes the jar does not know which one it got.

import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { zipSync } from 'fflate';

const projectDir = resolve(import.meta.dirname, '..');
const { version, description, config } = JSON.parse(readFileSync(join(projectDir, 'package.json'), 'utf8'));
const [groupId, artifactId] = config.uiJar.split(':');
const source = join(projectDir, 'dist', 'micro-frontends');
const dist = join(projectDir, 'dist');

function filesBelow(folder) {
  return readdirSync(folder).flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? filesBelow(path) : [path];
  });
}

const pom = `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <packaging>jar</packaging>
  <description>${description}</description>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0</url>
    </license>
  </licenses>
</project>
`;

// A fixed time for every entry, so the same files give the same jar.
const mtime = new Date('2000-01-01T00:00:00Z');
const text = (content) => [new TextEncoder().encode(content), { mtime }];
const zip = {
  'META-INF/MANIFEST.MF': text(`Manifest-Version: 1.0\r\nCreated-By: ${artifactId} build-ui-jar.mjs\r\nImplementation-Title: ${artifactId}\r\nImplementation-Version: ${version}\r\n\r\n`),
  [`META-INF/maven/${groupId}/${artifactId}/pom.xml`]: text(pom),
  [`META-INF/maven/${groupId}/${artifactId}/pom.properties`]: text(`groupId=${groupId}\nartifactId=${artifactId}\nversion=${version}\n`),
};
const files = filesBelow(source).sort();
for (const file of files) {
  const path = relative(source, file).split('\\').join('/');
  zip[`META-INF/resources/${config.workflowModuleId}/${path}`] = [readFileSync(file), { mtime }];
}

// A jar built by Maven has an entry for every folder too, and some class path scanners need them.
const folders = new Set(Object.keys(zip).flatMap((path) => path.split('/').slice(0, -1).map((_, index, parts) => `${parts.slice(0, index + 1).join('/')}/`)));
// Java's JarInputStream only finds the manifest when it comes first, right after META-INF/.
const folder = (path) => [path, [new Uint8Array(0), { mtime }]];
const withFolders = Object.fromEntries([
  folder('META-INF/'),
  ['META-INF/MANIFEST.MF', zip['META-INF/MANIFEST.MF']],
  ...[...folders].filter((path) => path !== 'META-INF/').sort().map(folder),
  ...Object.entries(zip).filter(([path]) => path !== 'META-INF/MANIFEST.MF'),
]);

const jar = join(dist, `${artifactId}-${version}.jar`);
writeFileSync(jar, zipSync(withFolders, { level: 9 }));
writeFileSync(join(dist, `${artifactId}-${version}.pom`), pom);
console.log(`build-ui-jar: ${relative(projectDir, jar)} with ${files.length} files below META-INF/resources/${config.workflowModuleId}/`);
