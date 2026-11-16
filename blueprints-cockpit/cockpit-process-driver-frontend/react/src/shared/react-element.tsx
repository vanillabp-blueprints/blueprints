// Makes a React component a web component with its own shadow DOM. defineBcElement of the module
// kit does the work: it creates the shadow root, hands the component the properties the cockpit
// sets, and turns calls like showNotification() into DOM events the cockpit answers. So the
// module needs no wrapper of its own for the cockpit's functions.

import type { ComponentType } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { defineBcElement, type BcElementProps } from '@vanillabp/bc-module-kit';

export function defineReactElement(name: string, Component: ComponentType<BcElementProps>) {
  const roots = new WeakMap<ShadowRoot, Root>();
  defineBcElement(name, {
    mount: (shadowRoot, props) => {
      const reactRoot = createRoot(shadowRoot);
      roots.set(shadowRoot, reactRoot);
      reactRoot.render(<Component {...props} />);
    },
    update: (shadowRoot, props) => roots.get(shadowRoot)?.render(<Component {...props} />),
    unmount: (shadowRoot) => {
      roots.get(shadowRoot)?.unmount();
      roots.delete(shadowRoot);
    },
  });
}
