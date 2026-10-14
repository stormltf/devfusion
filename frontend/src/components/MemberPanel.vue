<template>
  <div class="page-card">
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <h4>项目成员</h4>
      <el-button type="primary" @click="dialog = true" data-testid="member-add">添加成员</el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error" show-icon :closable="false" title="加载失败" style="margin-bottom: 12px"
    >
      <el-button size="small" type="primary" @click="load" data-testid="retry-load">重试</el-button>
    </el-alert>

    <el-table :data="members" v-loading="loading" data-testid="member-table">
      <el-table-column label="昵称" width="160">
        <template #default="{ row }"><UserCell :name="row.nickname" /></template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column label="项目角色" width="160">
        <template #default="{ row }">
          <el-select
            :model-value="row.roleCode"
            size="small"
            :disabled="row.roleCode === 'OWNER'"
            @change="(v) => changeRole(row, v)"
          >
            <el-option value="ADMIN" label="项目管理员" />
            <el-option value="MEMBER" label="成员" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" label="加入时间">
        <template #default="{ row }">{{ fmtTime(row.joinedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            v-if="row.roleCode !== 'OWNER'"
            size="small"
            type="danger"
            link
            :data-testid="`member-remove-${row.userId}`"
            @click="remove(row)"
          >移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="添加成员" width="420px">
      <el-form label-width="70px">
        <el-form-item label="用户">
          <el-select v-model="selected" placeholder="选择用户" style="width: 100%" data-testid="member-user-select">
            <el-option v-for="u in candidates" :key="u.id" :value="u.id" :label="`${u.nickname}（${u.username}）`" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save" data-testid="member-save">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addMember, listMembers, listSimpleUsers, removeMember, updateMember } from '../api'
import UserCell from './common/UserCell.vue'
import { fmtTime } from '../utils/enums'

const props = defineProps({ projectId: String })
const members = ref([])
const allUsers = ref([])
const dialog = ref(false)
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)
const loadError = ref(false)

const candidates = computed(() => {
  const ids = new Set(members.value.map((m) => m.userId))
  return allUsers.value.filter((u) => !ids.has(u.id))
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    members.value = (await listMembers(props.projectId)).data
    allUsers.value = (await listSimpleUsers()).data
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!selected.value) {
    ElMessage.warning('请选择用户')
    return
  }
  saving.value = true
  try {
    await addMember(props.projectId, { userId: selected.value, roleCode: 'MEMBER' })
    ElMessage.success('已添加')
    dialog.value = false
    selected.value = null
    await load()
  } finally {
    saving.value = false
  }
}

async function changeRole(row, roleCode) {
  await updateMember(props.projectId, row.userId, { roleCode })
  ElMessage.success('已更新')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认移除成员「${row.nickname}」？`, '提示', { type: 'warning' })
  await removeMember(props.projectId, row.userId)
  ElMessage.success('已移除')
  await load()
}

onMounted(load)
</script>
