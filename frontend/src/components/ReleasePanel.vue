<template>
  <div>
    <div class="toolbar">
      <h4>发布管理</h4>
      <el-button type="primary" @click="openCreate" data-testid="release-create">新建发布</el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error" show-icon :closable="false" title="加载失败" style="margin: 0 12px 12px"
    >
      <el-button size="small" type="primary" @click="load" data-testid="retry-load">重试</el-button>
    </el-alert>

    <div class="page-card">
      <el-table v-loading="loading" :data="releases" data-testid="release-table">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="releaseDate" label="发布日期" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="releaseType(row.status)">{{ releaseLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" label="关联工作项" width="110" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PLANNED'" link type="primary" size="small" :data-testid="`release-publish-${row.id}`" @click="changeStatus(row, 'RELEASED')">发布</el-button>
            <el-button v-if="row.status === 'RELEASED'" link type="warning" size="small" @click="changeStatus(row, 'ARCHIVED')">归档</el-button>
            <el-button link type="primary" size="small" @click="viewItems(row)">工作项</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createDialog" title="新建发布" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" data-testid="release-name" /></el-form-item>
        <el-form-item label="版本" required><el-input v-model="form.version" data-testid="release-version" placeholder="如 1.1.0" /></el-form-item>
        <el-form-item label="发布日期">
          <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="release-save">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemsVisible" :title="`发布工作项：${current.name || ''}`" width="640px">
      <el-table :data="current.items || []" size="small">
        <el-table-column prop="itemNo" label="编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="statusCode" label="状态" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeReleaseStatus, createRelease, deleteRelease, getRelease, listReleases } from '../api'
import { statusLabel, statusType } from '../utils/enums'

const props = defineProps({ projectId: String })
const releases = ref([])
const loading = ref(false)
const loadError = ref(false)
const createDialog = ref(false)
const saving = ref(false)
const form = reactive({ name: '', version: '', releaseDate: null, description: '' })
const itemsVisible = ref(false)
const current = ref({ items: [] })

const releaseLabel = (s) => statusLabel(s)
const releaseType = (s) => statusType(s)

async function load() {
  loading.value = true
  loadError.value = false
  try {
    releases.value = (await listReleases(props.projectId)).data
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { name: '', version: '', releaseDate: null, description: '' })
  createDialog.value = true
}

async function save() {
  if (!form.name || !form.version) {
    ElMessage.warning('请填写名称和版本')
    return
  }
  saving.value = true
  try {
    await createRelease(props.projectId, { ...form })
    ElMessage.success('创建成功')
    createDialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function changeStatus(row, status) {
  const verb = { RELEASED: '发布', ARCHIVED: '归档' }[status]
  await ElMessageBox.confirm(`确认${verb}发布「${row.name}（${row.version}）」？`, '提示', { type: 'warning' })
  await changeReleaseStatus(row.id, status)
  ElMessage.success('状态已更新')
  await load()
}

async function viewItems(row) {
  current.value = (await getRelease(row.id)).data
  itemsVisible.value = true
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除发布「${row.name}」？`, '提示', { type: 'warning' })
  await deleteRelease(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
