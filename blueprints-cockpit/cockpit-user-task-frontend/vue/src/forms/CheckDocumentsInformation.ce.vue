<!-- What the form of "Check documents" shows to read: the loan request and the risk assessment. -->
<script setup lang="ts">
import type { CheckDocumentsForm } from '../shared/api.ts';
import type { Texts } from '../shared/texts.ts';

defineProps<{ form: CheckDocumentsForm; texts: Texts; language: string }>();
</script>

<template>
  <dl>
    <dt>{{ texts.loanRequest }}</dt>
    <dd data-testid="check-documents-loan-request">{{ form.loanRequestId }}</dd>
    <dt>{{ texts.riskAssessment }}</dt>
    <dd data-testid="check-documents-risk">{{ form.riskAssessment }}</dd>
  </dl>
</template>

<style>
dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; margin: 0 0 1rem; }
dt { font-weight: 600; }
dd { margin: 0; white-space: pre-wrap; }
</style>
