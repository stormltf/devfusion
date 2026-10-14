<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <StatCard :label="card.label" :value="card.value" :hint="card.sub" />
      </el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <ChartCard title="近 7 天构建趋势" :option="trendOption" height="300px" />
      </el-col>
      <el-col :span="10">
        <div class="page-card">
          <div class="panel-title">最近部署</div>
          <el-table :data="d.recentDeployments || []" size="small" empty-text="暂无部署">
            <el-table-column prop="version" label="版本" min-width="140" show-overflow-tooltip />
            <el-table-column prop="applicationName" label="应用" width="110" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><StatusTag :code="row.status" domain="deploy" /></template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getGlobalDashboard } from '../api'
import StatCard from '../components/common/StatCard.vue'
import ChartCard from '../components/common/ChartCard.vue'
import StatusTag from '../components/common/StatusTag.vue'

const d = reactive({})

const cards = computed(() => [
  { label: '项目数', value: d.projectCount ?? '-' },
  { label: '应用数', value: d.applicationCount ?? '-' },
  { label: '构建次数', value: d.runCount ?? '-', hint: d.runCount ? `成功率 ${Math.round(((d.runSuccessCount || 0) / d.runCount) * 100)}%` : '' },
  { label: '部署次数', value: d.deploymentCount ?? '-', hint: d.deploymentCount ? `成功率 ${Math.round(((d.deploymentSuccessCount || 0) / d.deploymentCount) * 100)}%` : '' },
])

const trendOption = computed(() => ({
  grid: { left: 40, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: (d.runTrend || []).map((t) => t.date.slice(5)), axisLine: { lineStyle: { color: '#e5e6eb' } } },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    data: (d.runTrend || []).map((t) => t.count),
    type: 'line', smooth: true,
    lineStyle: { width: 2.5 }, itemStyle: {},
    areaStyle: { color: 'rgba(0,100,255,0.08)' },
  }],
}))

onMounted(async () => {
  const resp = await getGlobalDashboard()
  Object.assign(d, resp.data)
})
</script>

<style scoped>
.panel-title { margin-bottom: 10px; font-size: var(--df-font-lg); font-weight: 600; color: var(--df-text); }
</style>
