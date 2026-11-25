// The style every web component of this module starts with. vue-element.ts puts it into each
// shadow root, before the styles of the components. The styles of a component are in its
// <style> block, see the components named *.ce.vue.
//
// The button sits in the header of the cockpit, next to the buttons of the cockpit and of other
// modules. So it stays one line high and never grows with its text. The form takes the width the
// dialog of the cockpit gives it, and a long word wraps instead of widening the dialog.

export const STYLE = `
:host { display: block; min-width: 0; font-family: system-ui, sans-serif; font-size: 1rem; color: #1d2733; overflow-wrap: anywhere; }
:host([hidden]) { display: none; }
`;
