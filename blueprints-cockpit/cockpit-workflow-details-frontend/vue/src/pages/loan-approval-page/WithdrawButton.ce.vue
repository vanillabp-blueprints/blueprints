<!--
  The button "Withdraw loan request". It calls the module, and the module correlates the message
  which starts the event subprocess. Only a loan request which waits for its review has the
  button enabled.
-->
<script setup lang="ts">
import { ref } from 'vue';
import type { BcElementProps } from '@vanillabp/bc-module-kit';
import { loanApproval, type Case } from '@gui-client';
import { forgetCase, problemOf, type Problem, type Texts } from '@shared';
import ProblemText from './ProblemText.ce.vue';

const props = defineProps<{ loaded?: Case; texts: Texts; showNotification: BcElementProps['showNotification'] }>();
const emit = defineEmits<{ withdrawn: [] }>();

const state = ref<'open' | 'running' | 'withdrawn'>('open');
const problem = ref<Problem>();

async function withdraw() {
  state.value = 'running';
  problem.value = undefined;
  try {
    await loanApproval.withdrawLoanRequest({ loanRequestId: props.loaded!.loanRequestId });
    forgetCase(props.loaded!.loanRequestId);
    state.value = 'withdrawn';
    // A cockpit which cannot show notifications refuses; the page says it anyway.
    props.showNotification({ level: 'success', text: props.texts.withdrawn }).catch(() => {});
    emit('withdrawn');
  } catch (error) {
    state.value = 'open';
    problem.value = problemOf(error);
    props.showNotification({ level: 'error', text: props.texts[problemOf(error)] }).catch(() => {});
  }
}
</script>

<template>
  <div class="actions">
    <ProblemText :problem="problem" :texts="texts" test-id="case-withdraw-error" />
    <button type="button" data-testid="case-withdraw" :disabled="!loaded || loaded.status !== 'REQUESTED' || state !== 'open'"
      @click="withdraw">{{ texts.withdraw }}</button>
    <span v-if="state === 'withdrawn'" data-testid="case-withdrawn">{{ texts.withdrawn }}</span>
  </div>
</template>

<style>
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 1rem; }
button { font: inherit; padding: 0.5rem 1.25rem; border: 0; border-radius: 0.25rem; background: #b3261e; color: #fff; cursor: pointer; }
button:disabled { background: #b6c0ca; cursor: default; }
</style>
