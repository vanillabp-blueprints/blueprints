<!-- The message shown when the module API fails or does not know the case. -->
<script setup lang="ts">
import type { Problem, Texts } from '@shared';

defineProps<{ problem?: Problem; texts: Texts; testId: string }>();
</script>

<template>
  <p v-if="problem" class="problem" :data-testid="testId">{{ texts[problem] }}</p>
</template>

<style>
.problem { color: #b3261e; }
</style>
