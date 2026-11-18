// The dev shell checks the names of the web components before it draws them, the way a cockpit
// does. So a module whose names the cockpit would refuse shows the same note here, during
// development, and not only once it runs in a cockpit.
//
// A name is refused when
// - it is not a valid name of a custom element, or it is a reserved one,
// - it does not start with the prefix of its module and a hyphen,
// - or it starts with the prefix of another module which is longer than the module's own prefix.
//   A module 'taxi' may not use the name 'taxi-ride-confirm' when there is a module 'taxi-ride'.
// The check does not ask for the build id and does not look at the short name.

import { USER_TASK_FORM, elementPrefix, type GuiApiData } from '@vanillabp/bc-module-kit';
import type { ModuleHost } from '@vanillabp/bc-module-host';

/** Names the HTML standard does not allow for a custom element, although they have a hyphen. */
const RESERVED_NAMES = [
  'annotation-xml',
  'color-profile',
  'font-face',
  'font-face-src',
  'font-face-uri',
  'font-face-format',
  'font-face-name',
  'missing-glyph',
];

/** The characters the HTML standard allows in the name of a custom element after the first one. */
const NAME_CHARACTER = '[-.0-9_a-z\\u00B7\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u037D\\u037F-\\u1FFF\\u200C\\u200D\\u203F\\u2040\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD\\u{10000}-\\u{EFFFF}]';
const CUSTOM_ELEMENT_NAME = new RegExp(`^[a-z]${NAME_CHARACTER}*-${NAME_CHARACTER}*$`, 'u');

/**
 * Why a cockpit refuses a name, or undefined if it accepts it. The text names the module, the name
 * and the prefix, because it is a bug of the module and its author has to find it.
 *
 * @param moduleId  the module which wants the web component drawn
 * @param name      the name of the web component
 * @param moduleIds every module of the registry, the module itself included
 */
export function refusedElementName(moduleId: string, name: string, moduleIds: readonly string[]): string | undefined {
  const prefix = elementPrefix(moduleId);
  if (!CUSTOM_ELEMENT_NAME.test(name) || RESERVED_NAMES.includes(name)) {
    return `The module '${moduleId}' names the web component '${name}', which is not a valid name of a custom element. Its names have to start with '${prefix}-'.`;
  }
  if (!name.startsWith(`${prefix}-`)) {
    return `The module '${moduleId}' names the web component '${name}', but its names have to start with '${prefix}-'.`;
  }
  const longer = moduleIds
    .filter((id) => id !== moduleId)
    .map(elementPrefix)
    .find((other) => other.length > prefix.length && name.startsWith(`${other}-`));
  if (longer !== undefined) {
    return `The module '${moduleId}' names the web component '${name}', which starts with '${longer}-', the prefix of another module. Its names have to start with '${prefix}-' and not with the longer prefix of another module.`;
  }
  return undefined;
}

/**
 * Asks the data entry UserTaskForm of the module for the web components of a user task and checks
 * the names the host would draw. Returns the note for the first name a cockpit refuses, or
 * undefined. The host asks the entry again when it draws: the entry is loaded once, and asking it
 * twice only calls its function twice.
 */
export async function refusedUserTaskElements(host: ModuleHost, moduleId: string, userTask: GuiApiData): Promise<string | undefined> {
  const parts = await host.ask(moduleId, USER_TASK_FORM, [userTask]);
  if (!parts) return undefined;
  const moduleIds = host.registry.list().map(({ id }) => id);
  for (const name of Object.values(host.pickAreas(parts.element))) {
    const refused = refusedElementName(moduleId, name, moduleIds);
    if (refused !== undefined) return refused;
  }
  return undefined;
}
