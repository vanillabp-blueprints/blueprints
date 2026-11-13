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
