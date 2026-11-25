<!-- The open user tasks of the case, as the cockpit lists them. -->
<script setup lang="ts">
import { toRef } from 'vue';
import type { BcUserTask, BcWorkflow } from '@vanillabp/bc-module-kit';
import { useOpenUserTasks, type Texts } from '@shared';

const props = defineProps<{ workflow: BcWorkflow; texts: Texts; language: string; reload: number }>();
const { userTasks, failed } = useOpenUserTasks(toRef(props, 'workflow'), toRef(props, 'reload'));

function titleOf(userTask: BcUserTask) {
  const title = userTask.title as Record<string, string> | string | undefined;
  if (typeof title === 'string') return title;
  return title?.[props.language] ?? title?.en ?? Object.values(title ?? {})[0] ?? String(userTask.taskDefinition ?? userTask.id);
}
</script>

<template>
  <section data-testid="case-user-tasks">
    <h2>{{ texts.openUserTasks }}</h2>
    <p v-if="failed" class="problem" data-testid="case-user-tasks-error">{{ texts.userTasksFailed }}</p>
    <template v-if="userTasks === undefined">
      <p v-if="!failed">{{ texts.loading }}</p>
    </template>
    <p v-else-if="userTasks.length === 0" data-testid="case-no-user-tasks">{{ texts.noOpenUserTasks }}</p>
    <ul v-else>
      <li v-for="userTask in userTasks" :key="userTask.id" data-testid="case-user-task">{{ titleOf(userTask) }}</li>
    </ul>
  </section>
</template>

<style>
h2 { font-size: 1.125rem; margin: 0 0 0.75rem; }
ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
li { padding: 0.5rem 0.75rem; border: 1px solid #c9d1d9; border-radius: 0.25rem; }
.problem { color: #b3261e; }
</style>
