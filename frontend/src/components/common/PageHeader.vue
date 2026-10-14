<template>
  <div class="page-header">
    <div class="ph-left">
      <div class="ph-title-row">
        <el-icon v-if="onBack" class="ph-back" @click="$emit('back')"><ArrowLeft /></el-icon>
        <el-avatar v-if="avatarText" :size="34" class="ph-avatar">{{ avatarText }}</el-avatar>
        <div>
          <div class="ph-title">{{ title }}</div>
          <div v-if="subtitle" class="ph-sub">{{ subtitle }}</div>
        </div>
      </div>
    </div>
    <div class="ph-extra"><slot /></div>
  </div>
</template>
<script setup>
import { ArrowLeft } from '@element-plus/icons-vue'
defineProps({ title: String, subtitle: String, avatarText: String, onBack: Boolean })
defineEmits(['back'])
</script>
<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; gap: 12px; }
.ph-title-row { display: flex; align-items: center; gap: 10px; }
.ph-back { cursor: pointer; color: var(--df-text-3); font-size: 18px; }
.ph-back:hover { color: var(--df-primary); }
.ph-avatar { background: var(--df-primary); color: #fff; }
.ph-title { font-size: 17px; font-weight: 600; color: var(--df-text); line-height: 1.2; }
.ph-sub { font-size: var(--df-font-sm); color: var(--df-text-3); margin-top: 3px; }
.ph-extra { display: flex; gap: 8px; align-items: center; }
</style>
