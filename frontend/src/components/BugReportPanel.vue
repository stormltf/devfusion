<template>
  <div>
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="page-card stat-card">
          <div class="stat-value" style="color: var(--df-danger)">{{ report.unresolvedCount ?? 0 }}</div>
          <div class="stat-label">未解决缺陷</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="page-card stat-card">
          <div class="stat-value">{{ report.avgAgeDays ?? 0 }}</div>
          <div class="stat-label">未解决平均年龄（天）</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="page-card stat-card">
          <div class="stat-value" style="color: var(--df-warning)">{{ report.reopenCount ?? 0 }}</div>
          <div class="stat-label">重开次数</div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card" style="margin-bottom: 12px">
      <h4 style="margin: 0 0 8px">新建 vs 已解决（近 14 天）</h4>
      <div ref="trendRef" style="height: 280px"></div>
    </div>

    <el-row :gutter="12">
      <el-col :span="12">
        <div class="page-card">
          <h4 style="margin: 0 0 8px">按优先级分布</h4>
          <el-table :data="byPriorityRows" size="small">
            <el-table-column prop="label" label="优先级" />
            <el-table-column prop="value" label="数量" width="100" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <h4 style="margin: 0 0 8px">按负责人分布</h4>
          <el-table :data="byAssigneeRows" size="small">
            <el-table-column prop="label" label="负责人" />
            <el-table-column prop="value" label="数量" width="100" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getBugReport } from '../api'

const props = defineProps({ projectId: String })
const report = ref({})
const trendRef = ref(null)

const priorityNames = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const byPriorityRows = computed(() =>
  Object.entries(report.value.byPriority || {}).map(([k, v]) => ({ label: priorityNames[k] || k, value: v })))
const byAssigneeRows = computed(() =>
  Object.entries(report.value.byAssignee || {}).map(([k, v]) => ({ label: `用户#${k}`, value: v })))

onMounted(async () => {
  report.value = (await getBugReport(props.projectId)).data
  const trend = report.value.trend || []
  const chart = echarts.init(trendRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新建', '已解决'] },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((t) => t.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '新建', type: 'bar', itemStyle: { color: '#f53f3f' }, data: trend.map((t) => t.created) },
      { name: '已解决', type: 'bar', itemStyle: { color: '#00b42a' }, data: trend.map((t) => t.resolved) },
    ],
  })
})
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card { text-align: center; padding: 18px; }
.stat-value { font-size: 26px; font-weight: 700; color: var(--df-text); }
.stat-label { color: var(--df-text-3); font-size: var(--df-font-sm); margin-top: 4px; }
</style>
