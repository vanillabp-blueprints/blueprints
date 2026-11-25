<!-- The area 'actions': the button. It tells the other two areas when the loan request is withdrawn. -->
<script setup lang="ts">
import { ref } from 'vue';
import { useCase, useCasePage, type AreaMessage } from '@shared';
import WithdrawButton from './WithdrawButton.ce.vue';

const { props, workflow, loanRequestId, texts } = useCasePage();
const reload = ref(0);
const { loaded } = useCase(loanRequestId, reload);

function withdrawn() {
  reload.value += 1;
  // No such area in this cockpit means nobody to tell.
  for (const area of ['body', 'information']) workflow.value.sendTo(area, { withdrawn: true } satisfies AreaMessage).catch(() => {});
}
</script>

<template>
  <WithdrawButton :loaded="loaded" :texts="texts" :show-notification="props.showNotification" @withdrawn="withdrawn" />
</template>
