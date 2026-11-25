// The style every web component of this module starts with. vue-element.ts puts it into each
// shadow root, before the styles of the components. The styles of a component are in its
// <style> block, see the components named *.ce.vue.
//
// Long content must not break the page. A long word wraps instead of widening its area, and a
// long list scrolls in the area the cockpit gives it. The cockpit keeps the column with the
// information and the actions in place, so the button never scrolls away.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
`;
