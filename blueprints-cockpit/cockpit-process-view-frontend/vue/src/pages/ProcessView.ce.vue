<!--
  The page of a case: its process, drawn with bpmn-js, with markers for the passed steps and the
  current one.

  The component loads the BPMN XML and the history from the module API. At the same time it
  imports ./draw-process.ts with import(). That file brings bpmn-js, so bpmn-js is a chunk of its
  own, and the browser loads it only here. The cockpit sets the property 'workflow' again when the
  case changes, and the component then loads the history again and draws the markers anew.
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { BcWorkflow } from '@vanillabp/bc-module-kit';
import { loadProcess, loanRequestIdOf, problemOf, textsFor, useBcElement, type Problem } from '@shared';

const props = useBcElement();
const workflow = computed(() => props.value.workflow as BcWorkflow);
const texts = computed(() => textsFor(props.value.language));
const diagram = ref<HTMLDivElement>();
const state = ref<'loading' | 'drawn' | Problem>('loading');
const problem = computed(() => (state.value === 'notFound' || state.value === 'failed' ? state.value : undefined));

let removeDiagram: (() => void) | undefined;
let drawing = 0;

async function draw(loanRequestId: string) {
  const run = ++drawing;
  try {
    const [{ bpmnXml, history }, { drawProcess }] = await Promise.all([loadProcess(loanRequestId), import('./draw-process.ts')]);
    if (run !== drawing || !diagram.value) return;
    const remove = await drawProcess(diagram.value, bpmnXml, history);
    if (run !== drawing) {
      remove();
      return;
    }
    removeDiagram?.();
    removeDiagram = remove;
    state.value = 'drawn';
  } catch (error) {
    if (run === drawing) state.value = problemOf(error);
  }
}

// The cockpit sets 'workflow' again whenever the case changes, as a new object. So the watcher
// looks at what tells such a change, not at the object. The diagram is in the template from the
// start, so it is there when the first run draws.
watch(
  () => [loanRequestIdOf(workflow.value), workflow.value.updatedAt, workflow.value.endedAt] as const,
  ([loanRequestId]) => draw(loanRequestId),
  { immediate: true, flush: 'post' },
);

onBeforeUnmount(() => {
  drawing += 1;
  removeDiagram?.();
});
</script>

<template>
  <section data-testid="process-view">
    <h2>{{ texts.process }}</h2>
    <p v-if="problem" class="problem" data-testid="process-error">{{ texts[problem] }}</p>
    <p v-if="state === 'loading'" data-testid="process-loading">{{ texts.loading }}</p>
    <div ref="diagram" class="diagram" data-testid="process-diagram" :hidden="problem !== undefined" />
    <ul v-if="state === 'drawn'" class="legend">
      <li class="passed">{{ texts.passed }}</li>
      <li class="current">{{ texts.current }}</li>
    </ul>
  </section>
</template>

<style>
/*
  The diagram gets a height of its own. bpmn-js fits the process into the box it is given, and a
  box without a height has nothing to fit into.
*/
h2 { font-size: 1.125rem; margin: 0 0 0.75rem; }
.diagram { position: relative; height: 16rem; border: 1px solid #c9d1d9; border-radius: 0.25rem; }
.legend { display: flex; flex-wrap: wrap; gap: 1rem; margin: 0.5rem 0 0; padding: 0; list-style: none; }
.legend li::before { content: ''; display: inline-block; width: 0.75rem; height: 0.75rem; margin-right: 0.375rem; border: 2px solid; border-radius: 0.125rem; vertical-align: -0.125rem; }
.legend .passed::before { background: #e3f1e4; border-color: #2e7d32; }
.legend .current::before { background: #fff1cc; border-color: #b26a00; }
.problem { color: #b3261e; }
</style>
