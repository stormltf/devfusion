<template>
  <div>
    <div class="toolbar">
      <b style="color: var(--df-text)">效能度量</b>
      <el-radio-group v-model="days" @change="load" data-testid="metrics-days">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="14">近 14 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>
    <el-row :gutter="16">
      <el-col :span="4" v-for="c in cards" :key="c.label">
        <div class="page-card stat-card">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{ color: c.color }">{{ c.value }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 12px">按日趋势</div>
      <el-table :data="m.trend || []" size="small" data-testid="metrics-trend">
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="deployCount" label="部署次数" width="100" />
        <el-table-column prop="deploySuccessCount" label="成功部署" width="100" />
        <el-table-column prop="runCount" label="构建次数" width="100" />
        <el-table-column prop="runSuccessCount" label="成功构建" width="100" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMetrics } from '../api'

const days = ref(7)
const m = ref({})

const cards = computed(() => [
  { label: '部署频率(次)', value: m.value.deployFrequency ?? '-', color: 'var(--df-primary)' },
  { label: '部署成功率', value: pct(m.value.deploySuccessRate), color: 'var(--df-success)' },
  { label: '构建成功率', value: pct(m.value.buildSuccessRate), color: 'var(--df-success)' },
  { label: '平均构建耗时', value: ms(m.value.avgBuildDurationMs), color: 'var(--df-text-2)' },
  { label: '变更失败率', value: pct(m.value.changeFailRate), color: 'var(--df-warning)' },
  { label: '平均恢复时长(分)', value: m.value.avgRecoveryMinutes ?? '-', color: 'var(--df-danger)' },
])
const pct = (v) => (v == null ? '-' : `${v}%`)
const ms = (v) => (v == null ? '-' : `${(v / 1000).toFixed(1)}s`)

async function load() { m.value = (await getMetrics(days.value)).data }
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.stat-card { padding: 18px 20px; }
.stat-label { color: var(--df-text-3); font-size: var(--df-font-sm); }
.stat-value { font-size: 26px; font-weight: 700; margin: 6px 0; color: var(--df-text); line-height: 1.1; }
</style>
