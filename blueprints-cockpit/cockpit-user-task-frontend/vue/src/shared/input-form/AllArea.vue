<!-- The area 'all': everything in one web component, so no messages are needed. -->
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useArea, useCompletion, useFormData } from '../input-form.ts';
import CompleteButton from './CompleteButton.vue';
import InputField from './InputField.vue';
import ProblemText from './ProblemText.vue';

const area = useArea();
const { definition, testId, texts, language } = area;
const input = ref('');
const { form, loadProblem } = useFormData(area, (loaded) => { input.value = definition.initialInput(loaded); });
const completion = useCompletion(area);
const { state } = completion;
const problem = computed(() => loadProblem.value ?? completion.problem.value);
</script>

<template>
  <ProblemText :test-id="testId" :problem="problem" :texts="texts" />
  <template v-if="form">
    <component :is="definition.information" :form="form" :texts="texts" :language="language" />
    <InputField :test-id="testId" :label="definition.label(texts)" :value="input" :disabled="state !== 'open'" :texts="texts"
      @change="input = $event" />
    <CompleteButton :test-id="testId" :input="input" :state="state" :texts="texts" @complete="completion.complete(input)" />
  </template>
  <p v-else-if="!loadProblem">{{ texts.loading }}</p>
</template>
