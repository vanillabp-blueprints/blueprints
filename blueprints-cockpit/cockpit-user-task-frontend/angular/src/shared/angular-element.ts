// Makes an Angular component a web component with its own shadow DOM. defineBcElement of the
// module kit does the work: it creates the shadow root, hands the component the properties the
// cockpit sets, and turns calls like showNotification() into DOM events the cockpit answers. So
// the module needs no wrapper of its own for the cockpit's functions.
//
// All web components of this module share one Angular application. It runs without zone.js:
// zone.js would patch setTimeout, promises and events of the whole page, and the page belongs to
// the cockpit. Signals tell Angular when to draw again.

import { createComponent, Injector, provideZonelessChangeDetection, type ApplicationRef, type ComponentRef, type Provider, type Type } from '@angular/core';
import { createApplication } from '@angular/platform-browser';
import { defineBcElement, type BcElementProps } from '@vanillabp/bc-module-kit';
import { STYLE } from './style.ts';

/** The component of a web component. The cockpit's properties come in as the input 'props'. */
export type BcComponent = Type<unknown>;

let application: Promise<ApplicationRef> | undefined;

/** The application is created when the first web component is drawn, not when the file is loaded. */
function theApplication() {
  application ??= createApplication({ providers: [provideZonelessChangeDetection()] });
  return application;
}

interface Mounted {
  props: BcElementProps;
  component?: ComponentRef<unknown>;
  unmounted: boolean;
}

/**
 * @param providers what the component and its children may inject on top of the application's
 *   providers, for example the definition of a form
 */
export function defineAngularElement(name: string, component: BcComponent, providers: Provider[] = []) {
  const mounted = new WeakMap<ShadowRoot, Mounted>();
  defineBcElement(name, {
    mount: (shadowRoot, props) => {
      const style = document.createElement('style');
      style.textContent = STYLE;
      const host = document.createElement('div');
      shadowRoot.append(style, host);
      const state: Mounted = { props, unmounted: false };
      mounted.set(shadowRoot, state);
      // Creating the application takes a moment. The cockpit may update or remove the web
      // component meanwhile, so the latest properties are taken, and a removed one is not drawn.
      theApplication().then((applicationRef) => {
        if (state.unmounted) return;
        const elementInjector = Injector.create({ providers, parent: applicationRef.injector });
        state.component = createComponent(component, { environmentInjector: applicationRef.injector, elementInjector, hostElement: host });
        state.component.setInput('props', state.props);
        applicationRef.attachView(state.component.hostView);
      });
    },
    update: (shadowRoot, props) => {
      const state = mounted.get(shadowRoot);
      if (!state) return;
      state.props = props;
      state.component?.setInput('props', props);
    },
    unmount: (shadowRoot) => {
      const state = mounted.get(shadowRoot);
      if (!state) return;
      state.unmounted = true;
      state.component?.destroy();
      mounted.delete(shadowRoot);
    },
  });
}
