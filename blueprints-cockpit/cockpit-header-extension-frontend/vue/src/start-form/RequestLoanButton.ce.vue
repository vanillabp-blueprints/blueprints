<!--
  The button in the header. It opens the start form in a dialog of the cockpit and waits until
  the user closes it. A form closed with OK hands over the new loan request, and the button says
  so in a notification. A form closed with "Cancel" hands over nothing.
-->
<script setup lang="ts">
import { computed, ref } from 'vue';
import type { StartedLoanRequest } from '@gui-client';
import { textsFor, useBcElement } from '@shared';
import { START_FORM, START_FORM_ELEMENTS } from '@loan-approval';

const props = useBcElement();
const texts = computed(() => textsFor(props.value.language));
const open = ref(false);

async function requestLoan() {
  open.value = true;
  try {
    const started = (await props.value.openDialog({ entry: START_FORM, element: START_FORM_ELEMENTS.form })) as StartedLoanRequest | undefined;
    if (started?.loanRequestId) {
      // A cockpit which cannot show notifications refuses. The case is in the list anyway.
      props.value.showNotification({ level: 'success', text: `${texts.value.started} ${started.loanRequestId}` }).catch(() => {});
    }
  } finally {
    open.value = false;
  }
}
</script>

<template>
  <button type="button" data-testid="request-loan" :disabled="open" @click="requestLoan">{{ texts.requestLoan }}</button>
</template>

<style>
button { font: inherit; padding: 0.375rem 1rem; border: 0; border-radius: 0.25rem; background: #1f5fa6; color: #fff; cursor: pointer; white-space: nowrap; }
button:disabled { background: #b6c0ca; cursor: default; }
</style>
