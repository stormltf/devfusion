<template>
  <span class="user-cell">
    <el-avatar :size="20" class="uc-avatar">{{ initial }}</el-avatar>
    <span class="uc-name">{{ name || fallback }}</span>
  </span>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ name: { type: String, default: '' }, id: { type: [Number, String], default: null } })
const fallback = computed(() => (props.name ? '' : '-'))
const initial = computed(() => (props.name ? props.name.slice(0, 1) : '-'))
</script>
<style scoped>
.user-cell { display: inline-flex; align-items: center; gap: 6px; }
.uc-avatar { background: var(--df-primary-light); color: var(--df-primary); font-size: 11px; }
.uc-name { color: var(--df-text-2); font-size: var(--df-font); }
</style>
