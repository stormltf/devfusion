<template>
  <div class="page-card">
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <h4>迭代列表</h4>
      <el-button type="primary" @click="dialog = true" data-testid="iteration-create">新建迭代</el-button>
    </div>

    <el-table :data="iterations" :data-testid="`iteration-table`">
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="goal" label="目标" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="iterType(row.status)">{{ iterLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="200">
        <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="进度" width="160">
        <template #default="{ row }">
          <el-progress
            :percentage="row.totalCount ? Math.round((row.doneCount / row.totalCount) * 100) : 0"
            :stroke-width="10"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link :data-testid="`iteration-overview-${row.id}`" @click="openOverview(row)">概览</el-button>
          <el-button
            v-if="row.status === 'PLANNED'"
            size="small"
            type="primary"
            link
            :data-testid="`iteration-start-${row.id}`"
            @click="changeStatus(row, 'IN_PROGRESS')"
          >启动</el-button>
          <el-button
            v-if="row.status === 'IN_PROGRESS'"
            size="small"
            type="success"
            link
            :data-testid="`iteration-complete-${row.id}`"
            @click="changeStatus(row, 'COMPLETED')"
          >完成</el-button>
          <el-button
            v-if="row.status !== 'COMPLETED' && row.status !== 'CANCELED'"
            size="small"
            type="warning"
            link
            @click="changeStatus(row, 'CANCELED')"
          >取消</el-button>
          <el-button size="small" type="danger" link :data-testid="`iteration-delete-${row.id}`" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新建迭代" width="480px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="名称" required><el-input v-model="form.name" data-testid="iteration-name" /></el-form-item>
        <el-form-item label="目标"><el-input v-model="form.goal" type="textarea" /></el-form-item>
        <el-form-item label="起止">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="iteration-save">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="overviewVisible" size="620px" :title="overview.name || '迭代概览'">
      <template v-if="overview.id">
        <div class="ov-info">
          <div class="ov-item"><label>负责人</label><span>{{ overview.ownerName || '-' }}</span></div>
          <div class="ov-item"><label>起止</label><span>{{ overview.startDate || '-' }} ~ {{ overview.endDate || '-' }}</span></div>
          <div class="ov-item"><label>进度</label><span>{{ Math.round((overview.progress || 0) * 100) }}%（{{ overview.doneCount }}/{{ overview.totalCount }}）</span></div>
          <div class="ov-item"><label>故事点合计</label><span>{{ overview.storyPointSum ?? 0 }}</span></div>
          <div class="ov-item"><label>推荐容量</label><span>{{ overview.recommendedCapacity ?? '无历史，暂无' }}</span></div>
        </div>

        <div class="ov-stages" data-testid="iteration-stages">
          <div class="ov-stage"><b>{{ overview.todoCount ?? 0 }}</b><span>待处理</span></div>
          <el-icon class="ov-arrow"><Right /></el-icon>
          <div class="ov-stage"><b>{{ overview.inProgressCount ?? 0 }}</b><span>进行中</span></div>
          <el-icon class="ov-arrow"><Right /></el-icon>
          <div class="ov-stage"><b>{{ overview.doneCount ?? 0 }}</b><span>已完成</span></div>
        </div>

        <div class="ov-burndown-head">
          <b>燃尽图</b>
          <el-radio-group v-model="burndownMetric" size="small" @change="renderBurndown">
            <el-radio-button value="ITEM">工作项</el-radio-button>
            <el-radio-button value="STORY_POINT">故事点</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="burndownRef" style="height: 260px"></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Right } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { changeIterationStatus, createIteration, deleteIteration, getBurndown, getIteration, listIterations } from '../api'

const props = defineProps({ projectId: String })
const iterations = ref([])
const dialog = ref(false)
const range = ref([])
const form = reactive({ name: '', goal: '' })
const overviewVisible = ref(false)
const overview = ref({})
const burndownRef = ref(null)
const burndownMetric = ref('ITEM')

const iterLabel = (s) => ({ PLANNED: '规划中', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELED: '已取消' }[s] || s)
const iterType = (s) => ({ PLANNED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELED: 'danger' }[s] || 'info')

async function load() {
  const resp = await listIterations(props.projectId)
  iterations.value = resp.data
}

async function save() {
  if (!form.name) {
    ElMessage.warning('请填写迭代名称')
    return
  }
  await createIteration(props.projectId, {
    ...form,
    startDate: range.value?.[0],
    endDate: range.value?.[1],
  })
  ElMessage.success('创建成功')
  dialog.value = false
  form.name = ''
  form.goal = ''
  range.value = []
  await load()
}

async function changeStatus(row, status) {
  await changeIterationStatus(row.id, status)
  ElMessage.success(`迭代已${iterLabel(status)}`)
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除迭代「${row.name}」？`, '提示', { type: 'warning' })
  await deleteIteration(row.id)
  ElMessage.success('已删除')
  await load()
}

async function openOverview(row) {
  overview.value = (await getIteration(row.id)).data
  overviewVisible.value = true
  await nextTick()
  await renderBurndown()
}

async function renderBurndown() {
  if (!burndownRef.value || !overview.value.id) return
  const resp = await getBurndown(overview.value.id, burndownMetric.value)
  const points = resp.data || []
  const chart = echarts.init(burndownRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['实际剩余', '理想线'] },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: points.map((p) => p.date?.slice(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '实际剩余', type: 'line', smooth: true, itemStyle: { color: '#0064ff' }, data: points.map((p) => p.remaining) },
      { name: '理想线', type: 'line', itemStyle: { color: '#c9cdd4' }, lineStyle: { type: 'dashed' }, data: points.map((p) => p.ideal) },
    ],
  })
}

onMounted(load)
defineExpose({ load })
</script>

<style scoped>
.ov-info { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 16px; }
.ov-item { display: flex; flex-direction: column; gap: 2px; }
.ov-item label { color: var(--df-text-3); font-size: var(--df-font-sm); }
.ov-item span { color: var(--df-text); font-size: var(--df-font); }
.ov-stages { display: flex; align-items: center; gap: 12px; background: var(--df-bg); border-radius: var(--df-radius-sm); padding: 14px; margin-bottom: 16px; }
.ov-stage { flex: 1; text-align: center; }
.ov-stage b { display: block; font-size: 22px; color: var(--df-primary); }
.ov-stage span { color: var(--df-text-3); font-size: var(--df-font-sm); }
.ov-arrow { color: var(--df-text-3); }
.ov-burndown-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
</style>
