<template>
  <div class="page-card">
    <div class="toolbar">
      <b style="color: var(--df-text)">个人访问令牌（PAT）</b>
      <el-button type="primary" @click="createDialog = true" data-testid="token-create">创建令牌</el-button>
    </div>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
      title="令牌可用于 OpenAPI 触发流水线：POST /api/open/pipelines/{id}/trigger，请求头 Authorization: Bearer <令牌>。明文仅创建时展示一次，请妥善保存。" />
    <el-table :data="rows" data-testid="token-table">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="Token（脱敏）" min-width="200">
        <template #default="{ row }"><span style="font-family: monospace">dfpat-****{{ row.tokenHash ? row.tokenHash.slice(-6) : '' }}</span></template>
      </el-table-column>
      <el-table-column label="最近使用" width="160">
        <template #default="{ row }">{{ row.lastUsedAt ? fmtTime(row.lastUsedAt) : '从未使用' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" link @click="remove(row)" data-testid="token-delete">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createDialog" title="创建访问令牌" width="460px">
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="name" placeholder="如：CI 自动触发" data-testid="token-name" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="token-save">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDialog" title="令牌已创建" width="500px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" title="此明文令牌仅展示一次，关闭后无法再次查看" />
      <el-input :model-value="plain" readonly style="margin-top: 12px" data-testid="token-plain" />
      <template #footer><el-button type="primary" @click="showDialog = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createToken, deleteToken, listTokens } from '../api'
import { fmtTime } from '../utils/enums'

const rows = ref([])
const name = ref('')
const plain = ref('')
const createDialog = ref(false)
const showDialog = ref(false)
const saving = ref(false)

async function load() { rows.value = (await listTokens()).data }

async function save() {
  if (!name.value.trim()) {
    ElMessage.warning('请填写令牌名称')
    return
  }
  saving.value = true
  try {
    const resp = await createToken({ name: name.value.trim() })
    plain.value = resp.data.token
    createDialog.value = false
    showDialog.value = true
    name.value = ''
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除令牌「${row.name}」？`, '提示', { type: 'warning' })
  await deleteToken(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
</style>
