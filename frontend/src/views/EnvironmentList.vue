<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <span style="color: #999; font-size: 13px">部署目标环境由团队自建，type 仅作分类标签</span>
      <el-button type="primary" @click="dialog = true" data-testid="env-create">新建环境</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="e in envs" :key="e.id" style="margin-bottom: 16px">
        <div class="page-card env-card">
          <div style="display: flex; justify-content: space-between; align-items: center">
            <b>{{ e.name }}</b>
            <el-tag size="small" :type="envType(e.type)">{{ envLabel(e.type) }}</el-tag>
          </div>
          <div style="color: #999; font-size: 12px; margin: 6px 0">{{ e.key }} · {{ e.description || '暂无描述' }}</div>
          <div style="color: #666; font-size: 12px">主机：{{ e.hosts || '-' }}</div>
          <div style="color: #666; font-size: 12px; margin-top: 2px">
            部署审批：<el-tag size="small" :type="e.requireApproval ? 'warning' : 'info'">{{ e.requireApproval ? '需要审批' : '自动执行' }}</el-tag>
            <span v-if="e.hostGroupName" style="margin-left: 8px">主机组：{{ e.hostGroupName }}</span>
          </div>
          <div style="margin-top: 10px; text-align: right">
            <el-button size="small" link type="primary" :data-testid="`env-edit-${e.id}`" @click="edit(e)">编辑</el-button>
            <el-button size="small" link type="danger" :data-testid="`env-delete-${e.id}`" @click="remove(e)">删除</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
    <EmptyState v-if="!envs.length" title="暂无环境" description="部署目标环境由团队自建，点击右上角新建" action-text="新建环境" @action="dialog = true" />

    <el-dialog v-model="dialog" :title="editId ? '编辑环境' : '新建环境'" width="460px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="名称" required><el-input v-model="form.name" data-testid="env-name" /></el-form-item>
        <el-form-item label="标识" required><el-input v-model="form.key" data-testid="env-key" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option value="DEV" label="DEV" /><el-option value="TEST" label="TEST" />
            <el-option value="STAGING" label="STAGING" /><el-option value="PROD" label="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="主机"><el-input v-model="form.hosts" placeholder="逗号分隔的 IP 或域名" /></el-form-item>
        <el-form-item label="主机组">
          <el-select v-model="form.hostGroupId" clearable placeholder="不绑定主机组" style="width: 100%" data-testid="env-hostgroup">
            <el-option v-for="g in groups" :key="g.id" :value="g.id" :label="g.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署审批">
          <el-switch v-model="form.requireApproval" active-text="进入待审批" inactive-text="自动执行" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="env-save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createEnvironment, deleteEnvironment, listEnvironments, updateEnvironment, listHostGroups } from '../api'
import EmptyState from '../components/common/EmptyState.vue'

const envs = ref([])
const groups = ref([])
const dialog = ref(false)
const editId = ref(null)
const saving = ref(false)
const form = reactive({ name: '', key: '', type: 'TEST', description: '', hosts: '', hostGroupId: null, requireApproval: false })

const envType = (t) => ({ DEV: 'info', TEST: 'primary', STAGING: 'warning', PROD: 'danger' }[t] || 'info')
const envLabel = (t) => ({ DEV: '开发', TEST: '测试', STAGING: '预发', PROD: '生产' }[t] || t)

async function load() {
  envs.value = (await listEnvironments()).data
}

function edit(e) {
  editId.value = e.id
  Object.assign(form, { name: e.name, key: e.key, type: e.type, description: e.description || '', hosts: e.hosts || '', hostGroupId: e.hostGroupId || null, requireApproval: e.requireApproval === 1 })
  dialog.value = true
}

async function save() {
  if (!form.name || !form.key) {
    ElMessage.warning('请填写名称和标识')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, requireApproval: form.requireApproval ? 1 : 0 }
    if (editId.value) {
      await updateEnvironment(editId.value, payload)
    } else {
      await createEnvironment(payload)
    }
    ElMessage.success('已保存')
    dialog.value = false
    editId.value = null
    Object.assign(form, { name: '', key: '', type: 'TEST', description: '', hosts: '', hostGroupId: null, requireApproval: false })
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(e) {
  await ElMessageBox.confirm(`确认删除环境「${e.name}」？`, '提示', { type: 'warning' })
  await deleteEnvironment(e.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await load()
  groups.value = (await listHostGroups()).data || []
})
</script>

<style scoped>
.env-card { min-height: 120px; }
</style>
