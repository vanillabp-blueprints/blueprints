<!-- The area 'information': the data of the loan request. -->
<script setup lang="ts">
import { useCase, useCasePage, useReloadOnMessage } from '@shared';
import CaseInformation from './CaseInformation.ce.vue';
import ProblemText from './ProblemText.ce.vue';

const { workflow, loanRequestId, texts, language } = useCasePage();
const reload = useReloadOnMessage(workflow);
const { loaded, problem } = useCase(loanRequestId, reload);
</script>

<template>
  <ProblemText :problem="problem" :texts="texts" test-id="case-error" />
  <CaseInformation v-if="loaded" :loaded="loaded" :texts="texts" :language="language" />
  <p v-else-if="!problem">{{ texts.loading }}</p>
</template>
