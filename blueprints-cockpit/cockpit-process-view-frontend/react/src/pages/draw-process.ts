// Draws the process of a case with bpmn-js, and marks on it what the case has done so far.
//
// The page imports this file with import(), never with a static import. So esbuild puts it, with
// bpmn-js and the styles of bpmn-js, into a chunk of its own. The browser loads that chunk only
// when the page draws a process, and no other part of the module pays for it. The file knows no
// UI framework, so it is the same in the React and in the Vue frontend.
//
// bpmn-js comes from npm, under the bpmn.io license. The Viewer puts the bpmn.io logo into the
// corner of the diagram, and the license asks to keep it visible. Nothing here hides it.

import Viewer from 'bpmn-js/lib/Viewer';
// The build loads these two files as text, see scripts/build-micro-frontends.mjs.
import BPMN_JS_CSS from 'bpmn-js/dist/assets/bpmn-js.css';
import DIAGRAM_JS_CSS from 'bpmn-js/dist/assets/diagram-js.css';
import type { WorkflowElementHistory, WorkflowHistory } from '@gui-client';

/** passed: the case went through the element. current: the case stands there now. */
export type Marker = 'passed' | 'current';

/** The CSS classes bpmn-js puts on the elements. The tests look for them. */
export const MARKER_CLASSES: Record<Marker, string> = { passed: 'bc-passed', current: 'bc-current' };

/**
 * bpmn-js writes the colours of a shape into its style attribute. So a marker needs !important.
 * The first child of `.djs-visual` is the outline of the shape: the rectangle of a task, the
 * circle of an event.
 */
const MARKER_CSS = `
.djs-element.${MARKER_CLASSES.passed} .djs-visual > :first-child { fill: #e3f1e4 !important; stroke: #2e7d32 !important; }
.djs-element.${MARKER_CLASSES.current} .djs-visual > :first-child { fill: #fff1cc !important; stroke: #b26a00 !important; stroke-width: 3px !important; }
`;

/**
 * An element which has started and not ended is where the case stands. An element which ended,
 * or which was cancelled, is one it passed. An element can be in the history more than once, in a
 * loop for example. Then it is current as long as one of its runs is.
 */
export function markersOf(history: WorkflowHistory): Map<string, Marker> {
  const markers = new Map<string, Marker>();
  for (const element of history.elementsHistory ?? []) {
    if (!element.elementId) continue;
    const marker = markerOf(element);
    if (markers.get(element.elementId) !== 'current') markers.set(element.elementId, marker);
  }
  return markers;
}

const markerOf = (element: WorkflowElementHistory): Marker => (element.endTime || element.isCanceled ? 'passed' : 'current');

/**
 * The styles of bpmn-js go into the shadow root the diagram is drawn in. A style in the <head> of
 * the page would not reach it, and the styles of this module must not reach the cockpit.
 */
function addStyles(container: HTMLElement) {
  const root = container.getRootNode();
  const target = root instanceof ShadowRoot ? root : document.head;
  if (target.querySelector('style[data-bpmn-js]')) return;
  const style = document.createElement('style');
  style.dataset.bpmnJs = '';
  style.textContent = `${DIAGRAM_JS_CSS}\n${BPMN_JS_CSS}\n${MARKER_CSS}`;
  target.append(style);
}

/** What the page needs of the two services of bpmn-js it calls. */
interface Canvas {
  addMarker(elementId: string, className: string): void;
  zoom(level: 'fit-viewport'): void;
}
interface ElementRegistry {
  get(elementId: string): unknown;
}

/**
 * Draws the BPMN XML into the container and marks the elements of the history. The history may
 * name elements the diagram has not got, for example the process itself. They are left out.
 *
 * @returns a function which removes the diagram again
 */
export async function drawProcess(container: HTMLElement, bpmnXml: string, history: WorkflowHistory): Promise<() => void> {
  addStyles(container);
  const viewer = new Viewer({ container });
  try {
    await viewer.importXML(bpmnXml);
    const canvas = viewer.get<Canvas>('canvas');
    const elements = viewer.get<ElementRegistry>('elementRegistry');
    for (const [elementId, marker] of markersOf(history)) {
      if (elements.get(elementId)) canvas.addMarker(elementId, MARKER_CLASSES[marker]);
    }
    canvas.zoom('fit-viewport');
  } catch (error) {
    viewer.destroy();
    throw error;
  }
  return () => viewer.destroy();
}
