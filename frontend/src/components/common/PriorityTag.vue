<template>
  <el-tag :type="type" size="small" effect="plain">{{ label }}</el-tag>
</template>
<script setup>
import { computed } from 'vue'
import { priorityLabel, priorityType } from '../../utils/enums'
const props = defineProps({ value: { type: String, default: '' } })
const label = computed(() => priorityLabel(props.value))
const type = computed(() => priorityType(props.value))
</script>
