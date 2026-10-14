<template>
  <el-tag :type="type" size="small" effect="light" :data-testid="testid">{{ label }}</el-tag>
</template>
<script setup>
import { computed } from 'vue'
import { statusLabel, statusType } from '../../utils/enums'
const props = defineProps({ code: { type: String, default: '' }, domain: String, testid: String })
const label = computed(() => statusLabel(props.code, props.domain))
const type = computed(() => statusType(props.code, props.domain))
</script>
