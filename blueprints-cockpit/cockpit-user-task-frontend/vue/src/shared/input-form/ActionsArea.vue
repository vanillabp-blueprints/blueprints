<!--
  The area 'actions': the button. It gets the value of the input field from the area 'body'.
  'body' may have sent its first value before this area listened, so this area asks for it once
  it listens.
-->
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useArea, useCompletion, type AreaMessage } from '../input-form.ts';
import CompleteButton from './CompleteButton.vue';
import ProblemText from './ProblemText.vue';

const area = useArea();
const { testId, texts, userTask } = area;
const completion = useCompletion(area);
const { state, problem } = completion;
const input = ref('');

watch(userTask, (task, _, onCleanup) => {
  const subscription = task.receiveMessage((message) => {
    const { input: value } = (message ?? {}) as AreaMessage;
    if (typeof value === 'string') input.value = value;
  });
  subscription.ready.then(() => task.sendTo('body', { askForInput: true } satisfies AreaMessage)).catch(() => {});
  onCleanup(() => subscription.unsubscribe());
}, { immediate: true });

async function complete() {
  if (await completion.complete(input.value)) {
    userTask.value.sendTo('body', { completed: true } satisfies AreaMessage).catch(() => {});
  }
}
</script>

<template>
  <ProblemText :test-id="testId" :problem="problem" :texts="texts" />
  <CompleteButton :test-id="testId" :input="input" :state="state" :texts="texts" @complete="complete" />
</template>
