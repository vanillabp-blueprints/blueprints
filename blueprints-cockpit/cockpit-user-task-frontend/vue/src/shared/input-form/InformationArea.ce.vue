<!-- The area 'information': the data to read. -->
<script setup lang="ts">
import { useArea, useFormData } from '../input-form.ts';

const area = useArea();
const { definition, texts, language } = area;
const { form } = useFormData(area);
</script>

<template>
  <component :is="definition.information" v-if="form" :form="form" :texts="texts" :language="language" />
</template>
