<template>
  <div class="chart-card page-card">
    <div class="cc-head">
      <span class="cc-title">{{ title }}</span>
      <span v-if="subtitle" class="cc-sub">{{ subtitle }}</span>
      <div class="cc-extra"><slot name="extra" /></div>
    </div>
    <div ref="chartEl" class="cc-body" :style="{ height: height }"></div>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'

// 统一图表主题：ONES 蓝为主色板、细网格、tooltip、图例位置
const CHART_COLORS = ['#0064ff', '#00b42a', '#ff7d00', '#f53f3f', '#86909c', '#722ed1']

const props = defineProps({
  title: String,
  subtitle: String,
  option: { type: Object, required: true },
  height: { type: String, default: '280px' },
})
const chartEl = ref(null)
let chart = null

function buildOption() {
  return {
    color: CHART_COLORS,
    textStyle: { fontFamily: 'Inter, PingFang SC, sans-serif', fontSize: 12, color: '#4e5969' },
    grid: { left: 12, right: 16, top: 32, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', confine: true, textStyle: { fontSize: 12 } },
    legend: { bottom: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 8, textStyle: { color: '#4e5969' } },
    ...props.option,
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(), true)
}

onMounted(() => {
  chart = echarts.init(chartEl.value)
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})
function resize() { chart?.resize() }
watch(() => props.option, render, { deep: true })
</script>
<style scoped>
.chart-card { padding: 16px; }
.cc-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.cc-title { font-weight: 600; font-size: var(--df-font-lg); color: var(--df-text); }
.cc-sub { font-size: var(--df-font-sm); color: var(--df-text-3); }
.cc-extra { margin-left: auto; }
.cc-body { width: 100%; }
</style>
