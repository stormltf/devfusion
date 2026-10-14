<template>
  <div class="page-card">
    <div style="display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索编号 / 标题"
        style="width: 200px"
        clearable
        :data-testid="`item-search-${type.toLowerCase()}`"
        @change="load"
      />
      <el-select v-model="filters.statusCode" placeholder="状态" style="width: 130px" clearable @change="load">
        <el-option v-for="s in statuses" :key="s.code" :value="s.code" :label="s.name" />
      </el-select>
      <el-select v-model="filters.priority" placeholder="优先级" style="width: 120px" clearable @change="load">
        <el-option v-for="p in priorities" :key="p.value" :value="p.value" :label="p.label" />
      </el-select>
      <el-select v-model="filters.assigneeId" placeholder="负责人" style="width: 130px" clearable @change="load">
        <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" />
      </el-select>
      <el-select v-model="filters.iterationId" placeholder="迭代" style="width: 160px" clearable @change="load">
        <el-option v-for="it in iterations" :key="it.id" :value="it.id" :label="it.name" />
      </el-select>
      <el-select v-model="savedViewId" placeholder="已保存视图" style="width: 160px" clearable data-testid="view-select" @change="applyView">
        <el-option v-for="v in views" :key="v.id" :value="v.id" :label="v.name" />
      </el-select>
      <el-select v-model="sortField" placeholder="排序字段" style="width: 130px" @change="load" data-testid="sort-field">
        <el-option value="updatedAt" label="更新时间" />
        <el-option value="createdAt" label="创建时间" />
        <el-option value="priority" label="优先级" />
        <el-option value="storyPoint" label="故事点" />
        <el-option value="itemNo" label="编号" />
      </el-select>
      <el-select v-model="sortDir" style="width: 90px" @change="load" data-testid="sort-dir">
        <el-option value="desc" label="降序" />
        <el-option value="asc" label="升序" />
      </el-select>
      <el-button type="primary" style="margin-left: auto" @click="createDialog = true" :data-testid="`item-create-${type.toLowerCase()}`">
        新建{{ typeName }}
      </el-button>
    </div>

    <div v-if="selection.length" class="batch-bar" data-testid="batch-bar">
      <span>已选 {{ selection.length }} 项</span>
      <el-select v-model="batchForm.statusCode" placeholder="批量改状态" size="small" style="width: 120px">
        <el-option v-for="s in statuses" :key="s.code" :value="s.code" :label="s.name" />
      </el-select>
      <el-button size="small" @click="batchUpdate('statusCode')" data-testid="batch-apply-status">应用状态</el-button>
      <el-select v-model="batchForm.assigneeId" placeholder="批量指派" size="small" style="width: 120px" clearable>
        <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" />
      </el-select>
      <el-button size="small" @click="batchUpdate('assigneeId')" data-testid="batch-apply-assignee">应用指派</el-button>
      <el-select v-model="batchForm.iterationId" placeholder="批量改迭代" size="small" style="width: 140px" clearable>
        <el-option v-for="it in iterations" :key="it.id" :value="it.id" :label="it.name" />
      </el-select>
      <el-button size="small" @click="batchUpdate('iterationId')" data-testid="batch-apply-iteration">应用迭代</el-button>
      <el-button size="small" type="danger" @click="batchDelete" data-testid="batch-delete">批量删除</el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      show-icon
      :closable="false"
      title="加载失败"
      description="数据加载出错，请重试"
      style="margin-bottom: 12px"
    >
      <template #default>
        <el-button size="small" type="primary" @click="load" data-testid="retry-load">重试</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" :data-testid="`item-table-${type.toLowerCase()}`" @selection-change="(s) => (selection = s)">
      <el-table-column type="selection" width="40" />
      <el-table-column prop="itemNo" label="编号" width="140">
        <template #default="{ row }"><span style="color: var(--df-primary)">{{ row.itemNo }}</span></template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.statusCode)">{{ row.statusName || row.statusCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }"><PriorityTag :value="row.priority" /></template>
      </el-table-column>
      <el-table-column label="负责人" width="120">
        <template #default="{ row }"><UserCell :name="row.assigneeName" /></template>
      </el-table-column>
      <el-table-column prop="iterationName" label="迭代" width="150" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 新建 -->
    <el-dialog v-model="createDialog" :title="`新建${typeName}`" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" :data-testid="`item-title-${type.toLowerCase()}`" @keyup.enter="save" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="p in priorities" :key="p.value" :value="p.value" :label="p.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.assigneeId" style="width: 100%" clearable>
            <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属迭代">
          <el-select v-model="form.iterationId" style="width: 100%" clearable>
            <el-option v-for="it in iterations" :key="it.id" :value="it.id" :label="it.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" :data-testid="`item-save-${type.toLowerCase()}`">创建</el-button>
      </template>
    </el-dialog>

    <WorkItemDrawer v-if="detailId" :item-id="detailId" @close="detailId = null" @changed="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteItems,
  batchUpdateItems,
  createItem,
  listIterations,
  listItems,
  listSimpleUsers,
  listStatuses,
  listViews,
} from '../api'
import WorkItemDrawer from './WorkItemDrawer.vue'
import PriorityTag from './common/PriorityTag.vue'
import UserCell from './common/UserCell.vue'
import { fmtTime } from '../utils/enums'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({ projectId: String, type: String })
const route = useRoute()
const router = useRouter()

const rows = ref([])
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const size = 10
const loading = ref(false)
const loadError = ref(false)
const statuses = ref([])
const users = ref([])
const iterations = ref([])
const views = ref([])
const savedViewId = ref(null)
const sortField = ref(route.query.sort || 'updatedAt')
const sortDir = ref(route.query.direction || 'desc')
const selection = ref([])
const filters = reactive({
  keyword: route.query.keyword || '',
  statusCode: route.query.statusCode || '',
  priority: route.query.priority || '',
  assigneeId: route.query.assigneeId ? Number(route.query.assigneeId) : null,
  iterationId: route.query.iterationId ? Number(route.query.iterationId) : null,
})
const batchForm = reactive({ statusCode: '', assigneeId: null, iterationId: null })
const createDialog = ref(false)
const detailId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ title: '', description: '', priority: 'MEDIUM', assigneeId: null, iterationId: null })
const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
}

const priorities = [
  { value: 'URGENT', label: '紧急' },
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
]

const typeName = computed(() => ({ STORY: '需求', TASK: '任务', BUG: '缺陷' }[props.type]))

const statusTagType = (code) => {
  const s = statuses.value.find((x) => x.code === code)
  return { TODO: 'info', IN_PROGRESS: 'warning', DONE: 'success' }[s?.category] || 'info'
}

function syncQuery() {
  router.replace({ query: {
    ...route.query,
    keyword: filters.keyword || undefined,
    statusCode: filters.statusCode || undefined,
    priority: filters.priority || undefined,
    assigneeId: filters.assigneeId || undefined,
    iterationId: filters.iterationId || undefined,
    sort: sortField.value,
    direction: sortDir.value,
    page: page.value > 1 ? page.value : undefined,
  } }).catch(() => {})
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const resp = await listItems(props.projectId, {
      type: props.type,
      keyword: filters.keyword || undefined,
      statusCode: filters.statusCode || undefined,
      priority: filters.priority || undefined,
      assigneeId: filters.assigneeId || undefined,
      iterationId: filters.iterationId || undefined,
      sort: sortField.value || undefined,
      direction: sortDir.value || undefined,
      page: page.value,
      size,
    })
    rows.value = resp.data.records
    total.value = resp.data.total
    syncQuery()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function applyView(viewId) {
  const v = views.value.find((x) => x.id === viewId)
  if (!v) return
  let fj = {}
  try { fj = v.filterJson ? JSON.parse(v.filterJson) : {} } catch { fj = {} }
  Object.assign(filters, {
    keyword: fj.keyword || '',
    statusCode: fj.statusCode || '',
    priority: fj.priority || '',
    assigneeId: fj.assigneeId || null,
    iterationId: fj.iterationId || null,
  })
  sortField.value = fj.sortField || 'updatedAt'
  sortDir.value = fj.sortDir || 'desc'
  page.value = 1
  load()
}

async function batchUpdate(field) {
  const value = batchForm[field]
  if (value === '' || value === null) {
    ElMessage.warning('请先选择要应用的值')
    return
  }
  await batchUpdateItems(props.projectId, { itemIds: selection.value.map((r) => r.id), field, value })
  ElMessage.success('批量更新成功')
  await load()
}

async function batchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selection.value.length} 项？`, '提示', { type: 'warning' })
  await batchDeleteItems(props.projectId, { itemIds: selection.value.map((r) => r.id) })
  ElMessage.success('批量删除成功')
  await load()
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await createItem(props.projectId, { ...form, type: props.type })
    ElMessage.success('创建成功')
    createDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.title = ''
  form.description = ''
  form.assigneeId = null
  form.iterationId = null
  form.priority = 'MEDIUM'
  formRef.value?.clearValidate()
}

const openDetail = (row) => {
  detailId.value = row.id
}

onMounted(async () => {
  const [st, us, it, vw] = await Promise.all([
    listStatuses(props.projectId),
    listSimpleUsers(),
    listIterations(props.projectId),
    listViews(props.projectId),
  ])
  statuses.value = (st.data || []).filter((s) => s.type === props.type)
  users.value = us.data
  iterations.value = it.data
  views.value = vw.data
  await load()
})

defineExpose({ load })
</script>

<style scoped>
.batch-bar { display: flex; gap: 8px; align-items: center; background: var(--df-surface); border: 1px solid var(--df-primary); border-radius: var(--df-radius-sm); padding: 8px 12px; margin-bottom: 12px; font-size: var(--df-font); }
</style>
