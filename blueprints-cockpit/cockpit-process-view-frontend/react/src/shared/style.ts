// The styles of the case page. The web component puts them into its own shadow root. So they
// never reach the cockpit, and the styles of the cockpit never reach the page. The styles of
// bpmn-js and of the markers come with src/pages/draw-process.ts, into the same shadow root.
//
// The diagram gets a height of its own. bpmn-js fits the process into the box it is given, and a
// box without a height has nothing to fit into.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
h2 { font-size: 1.125rem; margin: 0 0 0.75rem; }
.diagram { position: relative; height: 16rem; border: 1px solid #c9d1d9; border-radius: 0.25rem; }
.legend { display: flex; flex-wrap: wrap; gap: 1rem; margin: 0.5rem 0 0; padding: 0; list-style: none; }
.legend li::before { content: ''; display: inline-block; width: 0.75rem; height: 0.75rem; margin-right: 0.375rem; border: 2px solid; border-radius: 0.125rem; vertical-align: -0.125rem; }
.legend .passed::before { background: #e3f1e4; border-color: #2e7d32; }
.legend .current::before { background: #fff1cc; border-color: #b26a00; }
.problem { color: #b3261e; }
`;
