// The style every web component of this module starts with. vue-element.ts puts it into each
// shadow root, before the styles of the components. The styles of a component are in its
// <style> block, see the components named *.ce.vue. The styles of bpmn-js and of the markers come
// with src/pages/draw-process.ts, into the same shadow root.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
`;
