<!-- The message shown when the module API fails or the user task is gone. -->
<script setup lang="ts">
import type { Texts } from '../texts.ts';
import type { Problem } from '../input-form.ts';

defineProps<{ testId: string; problem?: Problem; texts: Texts }>();
</script>

<template>
  <p v-if="problem" class="problem" :data-testid="`${testId}-error`">{{ texts[problem] }}</p>
</template>

<style>
.problem { color: #b3261e; }
</style>
