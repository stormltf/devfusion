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
    <el-row :gutter="12">
      <el-col :span="4" v-for="c in cards" :key="c.label">
        <StatCard :label="c.label" :value="c.value" :value-color="c.color" />
      </el-col>
    </el-row>

    <ChartCard title="按日趋势" :subtitle="`近 ${days} 天部署 / 构建成功量`" :option="trendOption" height="300px" style="margin-top: 16px" />

    <div class="page-card" style="margin-top: 16px">
      <div class="panel-title">按日明细</div>
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
import StatCard from '../components/common/StatCard.vue'
import ChartCard from '../components/common/ChartCard.vue'

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

const trendOption = computed(() => ({
  legend: { data: ['成功部署', '成功构建'] },
  xAxis: { type: 'category', data: (m.value.trend || []).map((t) => t.date) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    { name: '成功部署', type: 'bar', data: (m.value.trend || []).map((t) => t.deploySuccessCount) },
    { name: '成功构建', type: 'bar', data: (m.value.trend || []).map((t) => t.runSuccessCount) },
  ],
}))

async function load() { m.value = (await getMetrics(days.value)).data }
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.panel-title { font-weight: 600; margin-bottom: 10px; font-size: var(--df-font-lg); color: var(--df-text); }
</style>
