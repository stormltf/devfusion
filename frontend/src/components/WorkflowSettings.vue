<template>
  <div class="page-card">
    <h4 style="margin-bottom: 12px">工作流状态配置</h4>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
      title="每个工作项类型拥有独立的状态列表；TODO 起始、DONE 结束列在看板与统计中归类。" />

    <div style="display: flex; gap: 24px; flex-wrap: wrap">
      <div v-for="t in types" :key="t" style="flex: 1; min-width: 260px">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
          <b>{{ { STORY: '需求', TASK: '任务', BUG: '缺陷' }[t] }}</b>
          <el-button size="small" :data-testid="`status-add-${t}`" @click="openCreate(t)">添加状态</el-button>
        </div>
        <el-table :data="byType(t)" size="small" :data-testid="`status-table-${t}`">
          <el-table-column prop="name" label="状态" min-width="90" />
          <el-table-column label="归类" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="catType(row.category)">{{ catLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标记" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.isInitial" size="small" effect="plain">初始</el-tag>
              <el-tag v-if="row.isFinal" size="small" effect="plain" type="success">终态</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="" width="90">
            <template #default="{ row }">
              <el-button v-if="!row.isInitial" size="small" type="danger" link :data-testid="`status-del-${row.id}`" @click="remove(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialog" title="添加状态" width="400px">
      <el-form label-width="70px">
        <el-form-item label="名称" required><el-input v-model="form.name" data-testid="status-name" /></el-form-item>
        <el-form-item label="归类">
          <el-radio-group v-model="form.category">
            <el-radio value="TODO">待办</el-radio>
            <el-radio value="IN_PROGRESS">进行中</el-radio>
            <el-radio value="DONE">完成</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="status-save">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createStatus, deleteStatus, listStatuses } from '../api'

const props = defineProps({ projectId: String })
const statuses = ref([])
const dialog = ref(false)
const form = reactive({ type: 'STORY', name: '', category: 'TODO' })
const types = ['STORY', 'TASK', 'BUG']

const catLabel = (c) => ({ TODO: '待办', IN_PROGRESS: '进行中', DONE: '完成' }[c] || c)
const catType = (c) => ({ TODO: 'info', IN_PROGRESS: 'warning', DONE: 'success' }[c] || 'info')

const byType = (t) => statuses.value.filter((s) => s.type === t)

function openCreate(t) {
  form.type = t
  form.name = ''
  form.category = 'TODO'
  dialog.value = true
}

async function load() {
  statuses.value = (await listStatuses(props.projectId)).data
}

async function save() {
  if (!form.name) {
    ElMessage.warning('请填写状态名称')
    return
  }
  await createStatus(props.projectId, form)
  ElMessage.success('已添加')
  dialog.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除状态「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteStatus(props.projectId, row.id)
    ElMessage.success('已删除')
  } finally {
    await load()
  }
}

onMounted(load)
</script>
