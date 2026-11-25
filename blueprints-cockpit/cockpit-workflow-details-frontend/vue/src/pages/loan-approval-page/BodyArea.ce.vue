<!-- The area 'body': the open user tasks. -->
<script setup lang="ts">
import { useCasePage, useReloadOnMessage } from '@shared';
import UserTasks from './UserTasks.ce.vue';

const { workflow, texts, language } = useCasePage();
const reload = useReloadOnMessage(workflow);
</script>

<template>
  <UserTasks :workflow="workflow" :texts="texts" :language="language" :reload="reload" />
</template>
