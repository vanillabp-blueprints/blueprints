<!-- The data of the loan request, as the workflow module gives them. -->
<script setup lang="ts">
import type { Case } from '@gui-client';
import type { Texts } from '@shared';

defineProps<{ loaded: Case; texts: Texts; language: string }>();
</script>

<template>
  <dl>
    <dt>{{ texts.loanRequest }}</dt>
    <dd data-testid="case-loan-request">{{ loaded.loanRequestId }}</dd>
    <dt>{{ texts.applicant }}</dt>
    <dd data-testid="case-applicant">{{ loaded.applicant }}</dd>
    <dt>{{ texts.amount }}</dt>
    <dd data-testid="case-amount">{{ loaded.amount.toLocaleString(language) }}</dd>
    <dt>{{ texts.status }}</dt>
    <dd data-testid="case-status">{{ texts[loaded.status] }}</dd>
  </dl>
</template>

<style>
dl { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.25rem 1rem; margin: 0 0 1rem; }
dt { font-weight: 600; }
dd { margin: 0; white-space: pre-wrap; }
</style>
