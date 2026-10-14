<template>
  <div class="page-card">
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <div style="display: flex; gap: 8px">
        <el-select v-model="filters.appId" placeholder="全部应用" style="width: 180px" clearable @change="load">
          <el-option v-for="a in apps" :key="a.id" :value="a.id" :label="a.name" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate" data-testid="deploy-create">新建部署单</el-button>
    </div>

    <el-table :data="rows" data-testid="deploy-table">
      <el-table-column prop="id" label="单号" width="80" />
      <el-table-column label="应用" width="150">
        <template #default="{ row }">{{ row.applicationName }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" min-width="200" />
      <el-table-column prop="environmentName" label="目标环境" width="110" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.deployType === 'ROLLBACK' ? 'warning' : 'primary'">
            {{ row.deployType === 'ROLLBACK' ? '回滚' : '部署' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" data-testid="deploy-status-col">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)" :data-testid="`deploy-status-${row.id}`">
            {{ statusLabel(row.status) }}
            <el-icon v-if="row.status === 'DEPLOYING' || row.status === 'PENDING'" class="is-loading" style="margin-left: 2px">
              <Loading />
            </el-icon>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" :data-testid="`deploy-log-${row.id}`" @click="showLog(row)">日志</el-button>
          <el-button
            v-if="row.status === 'PENDING' || row.status === 'DEPLOYING'"
            size="small"
            type="warning"
            link
            :data-testid="`deploy-cancel-${row.id}`"
            @click="cancel(row)"
          >取消</el-button>
          <el-button
            v-if="row.status === 'PENDING_APPROVAL'"
            size="small" type="primary" link :data-testid="`deploy-approve-${row.id}`" @click="approve(row)"
          >审批通过</el-button>
          <el-button
            v-if="row.status === 'PENDING_APPROVAL'"
            size="small" type="danger" link :data-testid="`deploy-reject-${row.id}`" @click="reject(row)"
          >拒绝</el-button>
          <el-button
            v-if="row.status === 'SUCCESS'"
            size="small"
            type="danger"
            link
            :data-testid="`deploy-rollback-${row.id}`"
            @click="rollback(row)"
          >回滚</el-button>
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

    <!-- 新建部署单 -->
    <el-dialog v-model="createDialog" title="新建部署单" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="应用" required>
          <el-select v-model="form.applicationId" style="width: 100%" @change="onAppChange" data-testid="deploy-app-select">
            <el-option v-for="a in apps" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="制品" required>
          <el-select v-model="form.artifactId" style="width: 100%" data-testid="deploy-artifact-select">
            <el-option v-for="art in appArtifacts" :key="art.id" :value="art.id" :label="`${art.version}（${art.type}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标环境" required>
          <el-select v-model="form.environmentId" style="width: 100%" data-testid="deploy-env-select">
            <el-option v-for="e in envs" :key="e.id" :value="e.id" :label="`${e.name}（${e.type}${e.requireApproval ? '·需审批' : ''}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署模板">
          <el-select v-model="form.deployTemplateId" style="width: 100%" clearable placeholder="不使用模板" data-testid="deploy-template-select">
            <el-option v-for="t in templates" :key="t.id" :value="t.id" :label="t.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="save" data-testid="deploy-save">发起部署</el-button>
      </template>
    </el-dialog>

    <!-- 日志 -->
    <el-dialog v-model="logDialog" title="部署日志" width="720px" @opened="startLogPolling" @closed="stopLogPolling">
      <pre class="deploy-log" data-testid="deploy-log-content">{{ currentLog || '暂无日志' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  cancelDeployment,
  createDeployment,
  getDeployment,
  listApplications,
  listArtifacts,
  listDeployments,
  listEnvironments,
  rollbackDeployment,
  approveDeployment,
  rejectDeployment,
  listTemplates,
} from '../api'

const route = useRoute()
const rows = ref([])
const apps = ref([])
const envs = ref([])
const total = ref(0)
const page = ref(1)
const size = 15
const filters = reactive({ appId: null })
const createDialog = ref(false)
const logDialog = ref(false)
const currentLog = ref('')
const form = reactive({ applicationId: null, artifactId: null, environmentId: null, deployTemplateId: null })
const templates = ref([])
let timer = null

const appArtifacts = computed(() =>
  form.applicationId ? artifactsCache.value.filter((a) => a.applicationId === form.applicationId) : artifactsCache.value,
)
const artifactsCache = ref([])

const statusLabel = (s) => ({ PENDING: '排队中', DEPLOYING: '部署中', SUCCESS: '成功', FAILED: '失败', CANCELED: '已取消', PENDING_APPROVAL: '待审批', REJECTED: '已拒绝' }[s] || s)
const statusType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', DEPLOYING: 'warning', PENDING: 'info', PENDING_APPROVAL: 'warning' }[s] || 'info')
const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

async function load() {
  const resp = await listDeployments({ applicationId: filters.appId || undefined, page: page.value, size })
  rows.value = resp.data.records
  total.value = resp.data.total
  const busy = rows.value.some((r) => r.status === 'PENDING' || r.status === 'DEPLOYING')
  if (busy && !timer) {
    timer = setInterval(load, 2000)
  } else if (!busy && timer) {
    clearInterval(timer)
    timer = null
  }
}

function openCreate() {
  const qApp = Number(route.query.appId) || null
  Object.assign(form, { applicationId: qApp, artifactId: Number(route.query.artifactId) || null, environmentId: null, deployTemplateId: null })
  if (qApp) loadTemplates(qApp)
  createDialog.value = true
}

async function onAppChange() {
  form.artifactId = null
  form.deployTemplateId = null
  if (form.applicationId) await loadTemplates(form.applicationId)
}

async function loadTemplates(appId) {
  templates.value = (await listTemplates(appId)).data || []
}

async function save() {
  if (!form.applicationId || !form.artifactId || !form.environmentId) {
    ElMessage.warning('请完整选择应用、制品和目标环境')
    return
  }
  await createDeployment(form)
  ElMessage.success('部署单已发起')
  createDialog.value = false
  await load()
}

async function showLog(row) {
  logRowId.value = row.id
  const detail = await getDeployment(row.id)
  currentLog.value = detail.data.log || ''
  logDialog.value = true
}

/** 部署进行中日志持续增长：对话框打开期间轮询刷新 */
let logTimer = null
const logRowId = ref(null)

function startLogPolling() {
  stopLogPolling()
  logTimer = setInterval(async () => {
    if (!logRowId.value) return
    const d = (await getDeployment(logRowId.value)).data
    currentLog.value = d.log || ''
    if (['SUCCESS', 'FAILED', 'CANCELED'].includes(d.status)) stopLogPolling()
  }, 1500)
}

function stopLogPolling() {
  if (logTimer) {
    clearInterval(logTimer)
    logTimer = null
  }
}

async function cancel(row) {
  await cancelDeployment(row.id)
  ElMessage.success('已发送取消请求')
  await load()
}

async function rollback(row) {
  await ElMessageBox.confirm(
    `将「${row.applicationName}」在「${row.environmentName}」回滚到上一个成功版本，确认？`,
    '回滚确认',
    { type: 'warning' },
  )
  await rollbackDeployment(row.id)
  ElMessage.success('回滚部署单已发起')
  await load()
}

async function approve(row) {
  await approveDeployment(row.id)
  ElMessage.success('已审批通过，开始部署')
  await load()
}

async function reject(row) {
  await rejectDeployment(row.id)
  ElMessage.success('已拒绝，部署单终止')
  await load()
}

onMounted(async () => {
  apps.value = (await listApplications()).data
  envs.value = (await listEnvironments()).data
  artifactsCache.value = (await listArtifacts({ size: 100 })).data.records
  await load()
})

onBeforeUnmount(() => timer && clearInterval(timer))
</script>

<style scoped>
.deploy-log { margin: 0; padding: 12px; background: #1d2b3a; color: #c8e1ff; font-size: 12px; line-height: 1.8; max-height: 420px; overflow: auto; white-space: pre-wrap; }
</style>
