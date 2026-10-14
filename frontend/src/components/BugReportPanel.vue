<template>
  <div>
    <el-row :gutter="12">
      <el-col :span="8">
        <StatCard
          label="未解决缺陷" :value="report.unresolvedCount ?? 0"
          hint="当前状态非已关闭的缺陷" value-color="var(--df-danger)"
          clickable @click="$router.push(`/projects/${projectId}/bug`)" />
      </el-col>
      <el-col :span="8">
        <StatCard label="未解决平均年龄（天）" :value="report.avgAgeDays ?? 0" hint="按创建时间至今均值" />
      </el-col>
      <el-col :span="8">
        <StatCard label="重开次数" :value="report.reopenCount ?? 0" hint="历史标记重开累计" value-color="var(--df-warning)" />
      </el-col>
    </el-row>

    <ChartCard title="新建 vs 已解决" subtitle="近 14 天（口径：缺陷创建 / 已解决时间）" :option="trendOption" height="280px" />

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="12">
        <div class="page-card">
          <div class="sub-title">按优先级分布<span class="scope-note">统计范围：未解决缺陷</span></div>
          <el-table :data="byPriorityRows" size="small" empty-text="暂无未解决缺陷">
            <el-table-column label="优先级"><template #default="{row}"><PriorityTag :value="row.key" /></template></el-table-column>
            <el-table-column prop="value" label="数量" width="90" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <div class="sub-title">按负责人分布<span class="scope-note">统计范围：未解决缺陷</span></div>
          <el-table :data="byAssigneeRows" size="small" empty-text="暂无未解决缺陷">
            <el-table-column label="负责人"><template #default="{row}"><UserCell :name="row.assigneeName" /></template></el-table-column>
            <el-table-column prop="count" label="数量" width="90" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getBugReport } from '../api'
import StatCard from '../components/common/StatCard.vue'
import ChartCard from '../components/common/ChartCard.vue'
import UserCell from '../components/common/UserCell.vue'
import PriorityTag from '../components/common/PriorityTag.vue'

const props = defineProps({ projectId: String })
const report = ref({})

const trendOption = computed(() => {
  const trend = report.value.trend || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: ['新建', '已解决'] },
    xAxis: { type: 'category', data: trend.map((t) => t.date.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '新建', type: 'bar', itemStyle: { color: '#f53f3f' }, data: trend.map((t) => t.created) },
      { name: '已解决', type: 'bar', itemStyle: { color: '#00b42a' }, data: trend.map((t) => t.resolved) },
    ],
  }
})

const priorityNames = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const byPriorityRows = computed(() =>
  Object.entries(report.value.byPriority || {}).map(([k, v]) => ({ key: k, value: v, name: priorityNames[k] || k })))
const byAssigneeRows = computed(() => report.value.byAssignee || [])

onMounted(async () => {
  report.value = (await getBugReport(props.projectId)).data
})
</script>
<style scoped>
.sub-title { font-weight: 600; color: var(--df-text); margin-bottom: 8px; font-size: var(--df-font-lg); display: flex; align-items: center; gap: 8px; }
.scope-note { font-size: 12px; font-weight: 400; color: var(--df-text-3); }
</style>
