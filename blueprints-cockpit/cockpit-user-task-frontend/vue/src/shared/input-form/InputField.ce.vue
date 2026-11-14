<!-- The input field of a form, with its label. It tells every change through the event 'change'. -->
<script setup lang="ts">
import type { Texts } from '../texts.ts';

defineProps<{ testId: string; label: string; value: string; disabled: boolean; texts: Texts }>();
const emit = defineEmits<{ change: [value: string] }>();
</script>

<template>
  <div class="field" :data-testid="`${testId}-form`">
    <label for="input">{{ label }}</label>
    <textarea id="input" :data-testid="`${testId}-input`" :value="value" :disabled="disabled"
      @input="emit('change', ($event.target as HTMLTextAreaElement).value)"></textarea>
  </div>
</template>

<style>
.field { display: flex; flex-direction: column; gap: 0.375rem; }
label { font-weight: 600; }
textarea { font: inherit; min-height: 5rem; padding: 0.5rem; border: 1px solid #8a96a3; border-radius: 0.25rem; }
textarea:focus { outline: 2px solid #2f6fbd; outline-offset: 1px; }
</style>
