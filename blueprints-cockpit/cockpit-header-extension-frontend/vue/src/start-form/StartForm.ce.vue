<!--
  The start form. OK sends what the user typed to the module and closes the dialog with the id of
  the new loan request. The module refuses an empty applicant and an amount which is not
  positive, and the form checks the same before it sends anything. A refused or failed request
  keeps the dialog open, so the user can correct the values or try again.
-->
<script setup lang="ts">
import { computed, ref } from 'vue';
import { ResponseError, loanApproval } from '@gui-client';
import { textsFor, useBcElement } from '@shared';

type Problem = 'invalid' | 'refused' | 'failed';

const props = useBcElement();
const texts = computed(() => textsFor(props.value.language));
const applicant = ref('');
const amount = ref('');
const sending = ref(false);
const problem = ref<Problem>();

async function submit() {
  const amountAsNumber = Number(amount.value);
  if (applicant.value.trim() === '' || !Number.isInteger(amountAsNumber) || amountAsNumber < 1) {
    problem.value = 'invalid';
    return;
  }
  sending.value = true;
  problem.value = undefined;
  try {
    const started = await loanApproval.requestLoan({ loanRequest: { applicant: applicant.value.trim(), amount: amountAsNumber } });
    await props.value.closeDialog(started);
  } catch (error) {
    problem.value = error instanceof ResponseError && error.response.status === 400 ? 'refused' : 'failed';
    sending.value = false;
  }
}
</script>

<template>
  <form data-testid="start-form" novalidate @submit.prevent="submit">
    <h2>{{ texts.title }}</h2>
    <label>
      {{ texts.applicant }}
      <input v-model="applicant" data-testid="start-form-applicant" autofocus />
    </label>
    <label>
      {{ texts.amount }}
      <!-- A text model on purpose: the check above reads what the user typed, as the module does. -->
      <input v-model="amount" data-testid="start-form-amount" type="number" min="1" step="1" inputmode="numeric" />
    </label>
    <p v-if="problem" class="problem" data-testid="start-form-error">{{ texts[problem] }}</p>
    <div class="buttons">
      <button type="button" class="secondary" data-testid="start-form-cancel" :disabled="sending" @click="props.closeDialog()">
        {{ texts.cancel }}
      </button>
      <button type="submit" data-testid="start-form-ok" :disabled="sending">{{ sending ? texts.sending : texts.ok }}</button>
    </div>
  </form>
</template>

<style>
h2 { font-size: 1.125rem; margin: 0 0 1rem; }
form { display: grid; gap: 0.75rem; min-width: min(24rem, 100%); }
label { display: grid; gap: 0.25rem; font-weight: 600; }
input { font: inherit; padding: 0.375rem 0.5rem; border: 1px solid #8c99a6; border-radius: 0.25rem; min-width: 0; }
.buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; }
button { font: inherit; padding: 0.375rem 1rem; border: 0; border-radius: 0.25rem; background: #1f5fa6; color: #fff; cursor: pointer; white-space: nowrap; }
button.secondary { background: #e3e8ee; color: #1d2733; }
button:disabled { background: #b6c0ca; cursor: default; }
.problem { color: #b3261e; margin: 0; }
</style>
