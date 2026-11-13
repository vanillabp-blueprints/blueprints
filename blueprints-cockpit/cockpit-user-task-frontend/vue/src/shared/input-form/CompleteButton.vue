<!-- The button "Complete". It is enabled while the user task is open and the field is filled. -->
<script setup lang="ts">
import type { Texts } from '../texts.ts';

defineProps<{ testId: string; input: string; state: 'open' | 'running' | 'completed'; texts: Texts }>();
const emit = defineEmits<{ complete: [] }>();
</script>

<template>
  <div class="actions">
    <button type="button" :data-testid="`${testId}-complete`" :disabled="state !== 'open' || input.trim() === ''"
      @click="emit('complete')">{{ texts.complete }}</button>
    <span v-if="state === 'completed'" :data-testid="`${testId}-done`">{{ texts.completed }}</span>
  </div>
</template>
