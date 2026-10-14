<template>
  <div>
    <div class="page-card" style="margin-bottom: 12px">
      <el-row :gutter="12">
        <el-col :span="6" v-for="s in statCards" :key="s.label">
          <StatCard :label="s.label" :value="s.value" />
        </el-col>
      </el-row>
    </div>

    <div class="page-card">
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-input v-model="filters.keyword" placeholder="搜索用例" style="width: 200px" clearable @change="load" />
        <el-select v-model="filters.result" placeholder="执行结果" style="width: 130px" clearable @change="load">
          <el-option value="UNEXECUTED" label="未执行" />
          <el-option value="PASS" label="通过" />
          <el-option value="FAIL" label="失败" />
          <el-option value="BLOCKED" label="阻塞" />
        </el-select>
        <el-button type="primary" style="margin-left: auto" @click="dialog = true" data-testid="case-create">新建用例</el-button>
      </div>

      <el-alert
        v-if="loadError"
        type="error" show-icon :closable="false" title="加载失败" style="margin-bottom: 12px"
      >
        <el-button size="small" type="primary" @click="load" data-testid="retry-load">重试</el-button>
      </el-alert>

      <el-table :data="rows" v-loading="loading" :data-testid="`case-table`">
        <el-table-column prop="caseNo" label="编号" width="130" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><PriorityTag :value="row.priority" /></template>
        </el-table-column>
        <el-table-column label="最近结果" width="100">
          <template #default="{ row }"><StatusTag :code="row.latestResult" domain="testResult" /></template>
        </el-table-column>
        <el-table-column label="执行人" width="120">
          <template #default="{ row }"><UserCell :name="row.latestExecutorName" /></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" link :data-testid="`case-pass-${row.id}`" @click="exec(row, 'PASS')">通过</el-button>
            <el-button size="small" type="danger" link :data-testid="`case-fail-${row.id}`" @click="exec(row, 'FAIL')">失败</el-button>
            <el-button size="small" type="warning" link @click="exec(row, 'BLOCKED')">阻塞</el-button>
            <el-button size="small" link @click="edit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="dialog" :title="editId ? '编辑用例' : '新建用例'" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required><el-input v-model="form.title" data-testid="case-title" /></el-form-item>
        <el-form-item label="前置条件"><el-input v-model="form.precondition" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="步骤"><el-input v-model="form.steps" type="textarea" :rows="3" placeholder="描述操作步骤" /></el-form-item>
        <el-form-item label="预期结果"><el-input v-model="form.expected" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="模块"><el-input v-model="form.module" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 140px">
            <el-option value="URGENT" label="紧急" /><el-option value="HIGH" label="高" />
            <el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="case-save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTestCase, deleteTestCase, executeTestCase, getTestStats, listTestCases, updateTestCase } from '../api'
import StatCard from './common/StatCard.vue'
import PriorityTag from './common/PriorityTag.vue'
import StatusTag from './common/StatusTag.vue'
import UserCell from './common/UserCell.vue'
import { statusLabel } from '../utils/enums'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({ projectId: String })
const route = useRoute()
const router = useRouter()
const rows = ref([])
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const size = 10
const loading = ref(false)
const saving = ref(false)
const loadError = ref(false)
const stats = ref({})
const dialog = ref(false)
const editId = ref(null)
const filters = reactive({ keyword: route.query.keyword || '', result: route.query.result || '' })
const form = reactive({ title: '', precondition: '', steps: '', expected: '', module: '', priority: 'MEDIUM' })

function syncQuery() {
  router.replace({ query: {
    ...route.query,
    keyword: filters.keyword || undefined,
    result: filters.result || undefined,
    page: page.value > 1 ? page.value : undefined,
  } }).catch(() => {})
}

const statCards = computed(() => [
  { label: '用例总数', value: stats.value.total ?? 0 },
  { label: '通过', value: stats.value.PASS ?? 0 },
  { label: '失败', value: stats.value.FAIL ?? 0 },
  { label: '未执行', value: stats.value.UNEXECUTED ?? 0 },
])

const resultLabel = (r) => statusLabel(r)

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const resp = await listTestCases(props.projectId, {
      keyword: filters.keyword || undefined,
      result: filters.result || undefined,
      page: page.value,
      size,
    })
    rows.value = resp.data.records
    total.value = resp.data.total
    stats.value = (await getTestStats(props.projectId)).data
    syncQuery()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.title) {
    ElMessage.warning('请填写标题')
    return
  }
  saving.value = true
  try {
    if (editId.value) {
      await updateTestCase(editId.value, form)
    } else {
      await createTestCase(props.projectId, form)
    }
    ElMessage.success('已保存')
    dialog.value = false
    editId.value = null
    Object.assign(form, { title: '', precondition: '', steps: '', expected: '', module: '', priority: 'MEDIUM' })
    await load()
  } finally {
    saving.value = false
  }
}

function edit(row) {
  editId.value = row.id
  Object.assign(form, {
    title: row.title,
    precondition: row.precondition || '',
    steps: row.steps || '',
    expected: row.expected || '',
    module: row.module || '',
    priority: row.priority,
  })
  dialog.value = true
}

async function exec(row, result) {
  let bugItemId
  if (result === 'FAIL') {
    try {
      const r = await ElMessageBox.prompt('可填写关联缺陷的编号（工作项 ID，可留空）', '标记失败', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '缺陷工作项 ID',
      })
      bugItemId = r.value ? Number(r.value) : undefined
    } catch {
      return
    }
  }
  await executeTestCase(row.id, result, bugItemId)
  ElMessage.success(`已标记 ${resultLabel(result)}`)
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除用例「${row.title}」？`, '提示', { type: 'warning' })
  await deleteTestCase(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
