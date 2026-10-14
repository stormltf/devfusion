<template>
  <div class="page-card">
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <el-input v-model="keyword" placeholder="搜索用户名 / 昵称" style="width: 240px" clearable @change="load" />
      <el-button type="primary" @click="openCreate" data-testid="user-create">新建用户</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" data-testid="user-table">
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'">
            {{ row.role === 'ADMIN' ? '管理员' : '成员' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" :data-testid="`user-edit-${row.id}`" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="warning" :data-testid="`user-toggle-${row.id}`" @click="toggle(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" link type="info" :data-testid="`user-resetpw-${row.id}`" @click="resetPassword(row)">重置密码</el-button>
          <el-button size="small" link type="danger" :data-testid="`user-delete-${row.id}`" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialog" :title="editId ? '编辑用户' : '新建用户'" width="460px">
      <el-form :model="form" label-width="70px">
        <el-form-item v-if="!editId" label="用户名" required><el-input v-model="form.username" data-testid="user-username" /></el-form-item>
        <el-form-item v-if="!editId" label="密码" required><el-input v-model="form.password" type="password" data-testid="user-password" /></el-form-item>
        <el-form-item label="昵称" required><el-input v-model="form.nickname" data-testid="user-nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 140px">
            <el-option value="MEMBER" label="成员" /><el-option value="ADMIN" label="管理员" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="user-save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUser, deleteUser, listUsers, resetUserPassword, updateUser } from '../api'

const route = useRoute()
const router = useRouter()
const rows = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const size = 10
const keyword = ref((route.query.keyword || ''))
const dialog = ref(false)
const editId = ref(null)
const saving = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '', role: 'MEMBER' })

async function load() {
  loading.value = true
  try {
    const resp = await listUsers({ keyword: keyword.value || undefined, page: page.value, size })
    rows.value = resp.data.records
    total.value = resp.data.total
  } finally {
    loading.value = false
  }
  router.replace({ query: { ...(keyword.value ? { keyword: keyword.value } : {}), ...(page.value > 1 ? { page: page.value } : {}) } })
}

function openCreate() {
  editId.value = null
  Object.assign(form, { username: '', password: '', nickname: '', email: '', role: 'MEMBER' })
  dialog.value = true
}

function openEdit(row) {
  editId.value = row.id
  Object.assign(form, { username: row.username, password: '', nickname: row.nickname, email: row.email || '', role: row.role })
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    if (editId.value) {
      await updateUser(editId.value, { nickname: form.nickname, email: form.email, role: form.role })
    } else {
      if (!form.username || !form.password || !form.nickname) {
        ElMessage.warning('请完整填写用户名、密码和昵称')
        saving.value = false
        return
      }
      await createUser(form)
    }
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  await ElMessageBox.confirm(`确认${row.status === 1 ? '禁用' : '启用'}用户「${row.username}」？`, '提示', { type: 'warning' })
  await updateUser(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  await load()
}

async function resetPassword(row) {
  const { value } = await ElMessageBox.prompt(`为「${row.username}」设置新密码`, '重置密码', {
    inputPlaceholder: '请输入新密码',
    inputValue: '',
  })
  if (!value) { ElMessage.warning('请输入新密码'); return }
  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
