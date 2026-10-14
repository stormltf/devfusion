<template>
  <div class="page-card">
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <el-select v-model="appId" placeholder="全部应用" style="width: 200px" clearable @change="load">
        <el-option v-for="a in apps" :key="a.id" :value="a.id" :label="a.name" />
      </el-select>
      <span style="color: #999; font-size: 12px; align-self: center">构建成功后自动归档的制品（半成品库语义）</span>
    </div>
    <el-table :data="rows" v-loading="loading" data-testid="artifact-table">
      <el-table-column prop="version" label="版本" min-width="220" />
      <el-table-column label="应用" width="150">
        <template #default="{ row }">{{ appName(row.applicationId) }}</template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="制品阶段" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.stage === 'RELEASE' ? 'success' : 'info'" data-testid="artifact-stage">
            {{ row.stage === 'RELEASE' ? `RELEASE ${row.releaseVersion}` : 'SNAPSHOT' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ fmtSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="产生时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="goDeploy(row)">去部署</el-button>
          <el-button v-if="row.stage !== 'RELEASE'" size="small" type="warning" link data-testid="artifact-promote" @click="promote(row)">晋级</el-button>
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
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listApplications, listArtifacts, promoteArtifact } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../utils/enums'

const router = useRouter()
const route = useRoute()
const rows = ref([])
const loading = ref(false)
const apps = ref([])
const appId = ref(route.query.appId ? Number(route.query.appId) : null)
const total = ref(0)
const page = ref(Number(route.query.page) || 1)
const size = 15

const fmtSize = (b) => (b ? `${(b / 1024 / 1024).toFixed(1)} MB` : '-')
const appName = (id) => apps.value.find((a) => a.id === id)?.name || id

async function load() {
  loading.value = true
  try {
    const resp = await listArtifacts({ applicationId: appId.value || undefined, page: page.value, size })
    rows.value = resp.data.records
    total.value = resp.data.total
  } finally {
    loading.value = false
  }
  router.replace({ query: { ...(appId.value ? { appId: appId.value } : {}), ...(page.value > 1 ? { page: page.value } : {}) } })
}

function goDeploy(row) {
  router.push({ path: '/deployments', query: { appId: row.applicationId, artifactId: row.id } })
}

async function promote(row) {
  await ElMessageBox.confirm(`将制品「${row.version}」晋级为正式发布版本，确认？`, '晋级确认', { type: 'warning' })
  const resp = await promoteArtifact(row.id)
  ElMessage.success(`已晋级为成品 v${resp.data.releaseVersion}`)
  await load()
}

onMounted(async () => {
  apps.value = (await listApplications()).data
  await load()
})
</script>
