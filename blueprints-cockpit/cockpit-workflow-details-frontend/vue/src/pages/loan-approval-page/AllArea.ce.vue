<!-- The area 'all': everything in one web component, so no messages are needed. -->
<script setup lang="ts">
import { ref } from 'vue';
import { useCase, useCasePage } from '@shared';
import CaseInformation from './CaseInformation.ce.vue';
import ProblemText from './ProblemText.ce.vue';
import UserTasks from './UserTasks.ce.vue';
import WithdrawButton from './WithdrawButton.ce.vue';

const { props, workflow, loanRequestId, texts, language } = useCasePage();
const reload = ref(0);
const { loaded, problem } = useCase(loanRequestId, reload);
</script>

<template>
  <ProblemText :problem="problem" :texts="texts" test-id="case-error" />
  <CaseInformation v-if="loaded" :loaded="loaded" :texts="texts" :language="language" />
  <p v-else-if="!problem">{{ texts.loading }}</p>
  <UserTasks :workflow="workflow" :texts="texts" :language="language" :reload="reload" />
  <WithdrawButton :loaded="loaded" :texts="texts" :show-notification="props.showNotification" @withdrawn="reload += 1" />
</template>
