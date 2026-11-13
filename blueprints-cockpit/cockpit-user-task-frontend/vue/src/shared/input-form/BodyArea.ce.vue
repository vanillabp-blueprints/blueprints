<!-- The area 'body': the input field. It sends every change to the area 'actions'. -->
<script setup lang="ts">
import { ref, watch } from 'vue';
import type { BcUserTask } from '@vanillabp/bc-module-kit';
import { useArea, useFormData, type AreaMessage } from '../input-form.ts';
import InputField from './InputField.ce.vue';
import ProblemText from './ProblemText.ce.vue';

const area = useArea();
const { definition, testId, texts, userTask } = area;
const input = ref('');
const completed = ref(false);

function tellActions(task: BcUserTask, value: string) {
  // No 'actions' area in this cockpit means nobody to tell.
  task.sendTo('actions', { input: value } satisfies AreaMessage).catch(() => {});
}

function change(value: string) {
  input.value = value;
  tellActions(userTask.value, value);
}

const { form, loadProblem } = useFormData(area, (loaded) => change(definition.initialInput(loaded)));

watch(userTask, (task, _, onCleanup) => {
  const subscription = task.receiveMessage((message) => {
    const { askForInput, completed: done } = (message ?? {}) as AreaMessage;
    if (askForInput) tellActions(task, input.value);
    if (done) completed.value = true;
  });
  onCleanup(() => subscription.unsubscribe());
}, { immediate: true });
</script>

<template>
  <ProblemText :test-id="testId" :problem="loadProblem" :texts="texts" />
  <InputField v-if="form" :test-id="testId" :label="definition.label(texts)" :value="input" :disabled="completed" :texts="texts"
    @change="change" />
  <p v-else-if="!loadProblem">{{ texts.loading }}</p>
</template>
