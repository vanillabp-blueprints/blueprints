// Makes a Vue component a web component with its own shadow DOM. defineBcElement of the module
// kit does the work: it creates the shadow root, hands the component the properties the cockpit
// sets, and turns calls like showNotification() into DOM events the cockpit answers. So the
// module needs no wrapper of its own for the cockpit's functions.
//
// Each web component gets a small Vue application of its own, made with createApp. The properties
// of the cockpit go in through provide() as one shallow ref, and useBcElement() reads them. When
// the cockpit sets a property, the ref gets the new properties, and Vue draws again.
//
// The styles: each shadow root gets the style of src/shared/style.ts first. A component named
// *.ce.vue brings the styles of its <style> block as its list `styles`, see
// scripts/build-micro-frontends.mjs. When such a component is set up, useComponentStyles() puts
// the list into the shadow root it draws in, once per shadow root. That is what Vue's own
// defineCustomElement() does with the styles of *.ce.vue files. It is done here, because
// defineBcElement() of the kit, not Vue, makes the web component.

import { createApp, inject, shallowRef, type App, type Component, type InjectionKey, type ShallowRef } from 'vue';
import { defineBcElement, type BcElementProps } from '@vanillabp/bc-module-kit';
import { STYLE } from './style.ts';

const BC_ELEMENT_PROPS: InjectionKey<ShallowRef<BcElementProps>> = Symbol('BcElementProps');
const ADD_STYLES: InjectionKey<(component: object, styles: string[]) => void> = Symbol('AddStyles');

/** The properties the cockpit set on the web component. Vue draws again when they change. */
export function useBcElement() {
  const props = inject(BC_ELEMENT_PROPS);
  if (!props) throw new Error('useBcElement() works only inside a component of defineVueElement()');
  return props;
}

/**
 * Puts the styles of a *.ce.vue component into the shadow root it draws in. The build calls it in
 * the setup of every component which has a <style> block. Do not call it yourself.
 */
export function useComponentStyles(component: { styles?: string[] }) {
  inject(ADD_STYLES)?.(component, component.styles ?? []);
}

interface Mounted {
  app: App;
  props: ShallowRef<BcElementProps>;
}

/**
 * @param provides what the component and its children may inject on top of the properties of
 *   the cockpit, for example the definition of a form
 */
export function defineVueElement(name: string, component: Component, provides: [InjectionKey<unknown>, unknown][] = []) {
  const mounted = new WeakMap<ShadowRoot, Mounted>();
  defineBcElement(name, {
    mount: (shadowRoot, props) => {
      const style = document.createElement('style');
      style.textContent = STYLE;
      const host = document.createElement('div');
      // The cockpit may move the web component, which unmounts and mounts it again in the same
      // shadow root. Replacing the children removes the styles and the host of the mount before,
      // and the styles of the components come again when they are set up.
      shadowRoot.replaceChildren(style, host);
      const state: Mounted = { app: createApp(component), props: shallowRef(props) };
      state.app.provide(BC_ELEMENT_PROPS, state.props);
      // The components whose styles are in this shadow root already.
      const styled = new WeakSet<object>();
      state.app.provide(ADD_STYLES, (styledComponent, styles) => {
        if (styled.has(styledComponent)) return;
        styled.add(styledComponent);
        for (const css of styles) {
          const element = document.createElement('style');
          element.textContent = css;
          shadowRoot.insertBefore(element, host);
        }
      });
      for (const [key, value] of provides) state.app.provide(key, value);
      state.app.mount(host);
      mounted.set(shadowRoot, state);
    },
    update: (shadowRoot, props) => {
      const state = mounted.get(shadowRoot);
      if (state) state.props.value = props;
    },
    unmount: (shadowRoot) => {
      mounted.get(shadowRoot)?.app.unmount();
      mounted.delete(shadowRoot);
    },
  });
}
