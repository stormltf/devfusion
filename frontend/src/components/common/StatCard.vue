<template>
  <div class="stat-card" :class="{ clickable: !!clickable }" @click="$emit('click')" :data-testid="testid">
    <div class="sc-head">
      <span class="sc-label">{{ label }}</span>
      <span v-if="trend != null" class="sc-trend" :class="trend >= 0 ? 'up' : 'down'">
        {{ trend >= 0 ? '▲' : '▼' }}{{ Math.abs(trend) }}{{ trendUnit }}
      </span>
    </div>
    <div class="sc-value" :style="valueColor ? { color: valueColor } : {}">{{ value }}</div>
    <div v-if="hint" class="sc-hint">{{ hint }}</div>
  </div>
</template>
<script setup>
defineProps({
  label: String,
  value: [String, Number],
  hint: String,
  trend: Number,
  trendUnit: { type: String, default: '%' },
  valueColor: String,
  clickable: Boolean,
  testid: String,
})
defineEmits(['click'])
</script>
<style scoped>
.stat-card {
  background: var(--df-surface);
  border: 1px solid var(--df-border-light);
  border-radius: var(--df-radius);
  padding: 16px 18px;
}
.stat-card.clickable { cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.stat-card.clickable:hover { border-color: var(--df-primary); box-shadow: 0 2px 8px rgba(0,100,255,.08); }
.sc-head { display: flex; justify-content: space-between; align-items: center; }
.sc-label { color: var(--df-text-3); font-size: var(--df-font-sm); }
.sc-trend { font-size: 12px; font-weight: 600; }
.sc-trend.up { color: var(--df-success); }
.sc-trend.down { color: var(--df-danger); }
.sc-value { font-size: 28px; font-weight: 700; color: var(--df-text); line-height: 1.2; margin: 8px 0 2px; }
.sc-hint { font-size: 12px; color: var(--df-text-3); }
</style>
