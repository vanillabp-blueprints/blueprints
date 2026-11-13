<!-- What the form of "Assess risk" shows to read: the loan request and its amount. -->
<script setup lang="ts">
import type { AssessRiskForm } from '../shared/api.ts';
import type { Texts } from '../shared/texts.ts';

defineProps<{ form: AssessRiskForm; texts: Texts; language: string }>();
</script>

<template>
  <dl>
    <dt>{{ texts.loanRequest }}</dt>
    <dd data-testid="assess-risk-loan-request">{{ form.loanRequestId }}</dd>
    <dt>{{ texts.amount }}</dt>
    <dd data-testid="assess-risk-amount">{{ form.amount.toLocaleString(language) }}</dd>
  </dl>
</template>

<style>
dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; margin: 0 0 1rem; }
dt { font-weight: 600; }
dd { margin: 0; white-space: pre-wrap; }
</style>
