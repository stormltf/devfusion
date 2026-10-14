<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="page-card stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-sub" v-if="card.sub">{{ card.sub }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <div class="page-card panel">
          <h4 class="panel-title">近 7 天构建趋势</h4>
          <div ref="trendRef" style="height: 300px"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="page-card panel">
          <h4 class="panel-title">最近部署</h4>
          <el-table :data="d.recentDeployments || []" size="small">
            <el-table-column prop="version" label="版本" min-width="150" />
            <el-table-column prop="applicationName" label="应用" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { getGlobalDashboard } from '../api'

const d = reactive({})
const trendRef = ref()

const cards = computed(() => [
  { label: '项目数', value: d.projectCount ?? '-' },
  { label: '应用数', value: d.applicationCount ?? '-' },
  {
    label: '构建次数',
    value: d.runCount ?? '-',
    sub: d.runCount ? `成功率 ${Math.round(((d.runSuccessCount || 0) / d.runCount) * 100)}%` : '',
  },
  {
    label: '部署次数',
    value: d.deploymentCount ?? '-',
    sub: d.deploymentCount ? `成功率 ${Math.round(((d.deploymentSuccessCount || 0) / d.deploymentCount) * 100)}%` : '',
  },
])

function statusType(s) {
  return { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', DEPLOYING: 'warning' }[s] || 'info'
}

onMounted(async () => {
  const resp = await getGlobalDashboard()
  Object.assign(d, resp.data)
  const chart = echarts.init(trendRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: (d.runTrend || []).map((t) => t.date.slice(5)), axisLine: { lineStyle: { color: '#e5e6eb' } } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      data: (d.runTrend || []).map((t) => t.count),
      type: 'line', smooth: true,
      lineStyle: { color: '#0064ff', width: 2.5 },
      itemStyle: { color: '#0064ff' },
      areaStyle: { color: 'rgba(0,100,255,0.08)' },
    }],
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
  })
  window.addEventListener('resize', () => chart.resize())
})
</script>

<style scoped>
.stat-card { padding: 18px 20px; }
.stat-label { color: var(--df-text-3); font-size: var(--df-font-sm); }
.stat-value { font-size: 30px; font-weight: 700; margin: 6px 0; color: var(--df-text); line-height: 1.1; }
.stat-sub { color: var(--df-success); font-size: var(--df-font-sm); }
.panel { padding: 16px; }
.panel-title { margin-bottom: 10px; font-size: var(--df-font-lg); font-weight: 600; color: var(--df-text); }
</style>
