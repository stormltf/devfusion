<template>
  <div>
    <div class="toolbar">
      <h4>测试计划</h4>
      <el-button type="primary" @click="openCreate" data-testid="plan-create">新建测试计划</el-button>
    </div>

    <div class="page-card">
      <el-table :data="plans" data-testid="plan-table">
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="stage" label="阶段" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="planType(row.status)">{{ planLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.caseCount ? Math.round((row.executedCount / row.caseCount) * 100) : 0" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="结果" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="success">通过 {{ row.passCount }}</el-tag>
            <el-tag size="small" type="danger">失败 {{ row.failCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :data-testid="`plan-detail-${row.id}`" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createDialog" title="新建测试计划" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" data-testid="plan-name" /></el-form-item>
        <el-form-item label="阶段"><el-input v-model="form.stage" placeholder="如 SIT / UAT" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="plan-save">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="640px" :title="detail.name || '测试计划详情'">
      <template v-if="detail.id">
        <div class="detail-meta">
          <el-tag size="small" :type="planType(detail.status)">{{ planLabel(detail.status) }}</el-tag>
          <span v-if="detail.stage">阶段：{{ detail.stage }}</span>
          <span>用例 {{ detail.caseCount }} · 已执行 {{ detail.executedCount }}</span>
        </div>

        <div class="add-row">
          <el-select v-model="newCaseId" placeholder="从用例库选择加入" style="width: 260px" filterable>
            <el-option v-for="c in availableCases" :key="c.id" :value="c.id" :label="`${c.caseNo} ${c.title}`" />
          </el-select>
          <el-button type="primary" plain @click="addCase" data-testid="plan-add-case">加入用例</el-button>
        </div>

        <el-table :data="detail.cases || []" data-testid="plan-case-table" size="small">
          <el-table-column prop="caseNo" label="编号" width="120" />
          <el-table-column prop="title" label="用例" min-width="160" show-overflow-tooltip />
          <el-table-column label="执行人" width="130">
            <template #default="{ row }">
              <el-select :model-value="row.executorId" size="small" clearable @change="(v) => setExecutor(row, v)">
                <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="resultType(row.result)">{{ resultLabel(row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" size="small" :data-testid="`plan-pass-${row.caseId}`" @click="exec(row, 'PASS')">通过</el-button>
              <el-button link type="danger" size="small" :data-testid="`plan-fail-${row.caseId}`" @click="exec(row, 'FAIL')">失败</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addPlanCases, createTestPlan, deleteTestPlan, executePlanCase, getTestPlan,
  listTestCases, listTestPlans, listSimpleUsers, setPlanExecutors,
} from '../api'

const props = defineProps({ projectId: String })
const plans = ref([])
const createDialog = ref(false)
const form = ref({ name: '', stage: '' })
const detailVisible = ref(false)
const detail = ref({ cases: [] })
const users = ref([])
const allCases = ref([])
const newCaseId = ref(null)

const planLabel = (s) => ({ PLANNED: '规划中', IN_PROGRESS: '进行中', DONE: '已完成', CANCELED: '已取消' }[s] || s)
const planType = (s) => ({ PLANNED: 'info', IN_PROGRESS: 'primary', DONE: 'success', CANCELED: 'danger' }[s] || 'info')
const resultLabel = (r) => ({ UNEXECUTED: '未执行', PASS: '通过', FAIL: '失败', BLOCKED: '阻塞', SKIPPED: '跳过' }[r] || r)
const resultType = (r) => ({ PASS: 'success', FAIL: 'danger', BLOCKED: 'warning' }[r] || 'info')

const availableCases = computed(() => {
  const used = new Set((detail.value.cases || []).map((c) => c.caseId))
  return allCases.value.filter((c) => !used.has(c.id))
})

async function load() {
  plans.value = (await listTestPlans(props.projectId)).data
}

function openCreate() {
  form.value = { name: '', stage: '' }
  createDialog.value = true
}

async function save() {
  if (!form.value.name) {
    ElMessage.warning('请填写名称')
    return
  }
  await createTestPlan(props.projectId, form.value)
  ElMessage.success('创建成功')
  createDialog.value = false
  await load()
}

async function openDetail(row) {
  detail.value = (await getTestPlan(row.id)).data
  detailVisible.value = true
}

async function addCase() {
  if (!newCaseId.value) return
  await addPlanCases(detail.value.id, { caseIds: [newCaseId.value] })
  newCaseId.value = null
  ElMessage.success('已加入')
  detail.value = (await getTestPlan(detail.value.id)).data
}

async function setExecutor(row, userId) {
  await setPlanExecutors(detail.value.id, { assignments: [{ caseId: row.caseId, executorId: userId }] })
  row.executorId = userId
}

async function exec(row, result) {
  await executePlanCase(detail.value.id, { caseId: row.caseId, result })
  ElMessage.success(`已标记 ${resultLabel(result)}`)
  detail.value = (await getTestPlan(detail.value.id)).data
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除测试计划「${row.name}」？`, '提示', { type: 'warning' })
  await deleteTestPlan(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  const [us, cs] = await Promise.all([listSimpleUsers(), listTestCases(props.projectId, { size: 100 })])
  users.value = us.data
  allCases.value = cs.data.records
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.detail-meta { display: flex; gap: 16px; align-items: center; color: var(--df-text-2); font-size: var(--df-font); margin-bottom: 14px; }
.add-row { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
