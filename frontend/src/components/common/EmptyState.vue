<template>
  <div class="empty-state">
    <div class="es-icon">
      <svg viewBox="0 0 64 64" width="56" height="56" fill="none">
        <rect x="12" y="18" width="40" height="34" rx="4" stroke="#c9cdd4" stroke-width="2"/>
        <path d="M12 28h40M22 18v-6h20v6" stroke="#c9cdd4" stroke-width="2"/>
        <circle cx="32" cy="40" r="5" stroke="#c9cdd4" stroke-width="2"/>
      </svg>
    </div>
    <div class="es-title">{{ title || '暂无数据' }}</div>
    <div v-if="description" class="es-desc">{{ description }}</div>
    <el-button v-if="actionText" type="primary" size="small" class="es-action" @click="$emit('action')">{{ actionText }}</el-button>
  </div>
</template>
<script setup>
defineProps({ title: String, description: String, actionText: String })
defineEmits(['action'])
</script>
<style scoped>
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 36px 16px; text-align: center; }
.es-icon { margin-bottom: 10px; }
.es-title { font-size: var(--df-font); color: var(--df-text-2); }
.es-desc { font-size: var(--df-font-sm); color: var(--df-text-3); margin-top: 6px; max-width: 320px; }
.es-action { margin-top: 14px; }
</style>
