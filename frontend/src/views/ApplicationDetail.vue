<template>
  <div>
    <div class="page-card app-detail-head">
      <el-button :icon="ArrowLeft" circle plain size="small" @click="$router.push('/applications')" />
      <el-avatar :size="36" class="ad-avatar">{{ (app.name || '?').slice(0, 1).toUpperCase() }}</el-avatar>
      <div class="ad-meta">
        <span class="ad-name">{{ app.name }}</span>
        <span class="ad-sub">{{ app.key }} · {{ app.language }} · {{ app.repoUrl }}</span>
      </div>
      <el-button type="primary" data-testid="pipeline-create" @click="createPipelineDialog = true">新建流水线</el-button>
    </div>

    <div class="page-card">
      <el-tabs>
        <el-tab-pane label="流水线">
          <el-table :data="pipelines" data-testid="pipeline-table">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="触发方式" width="110">
              <template #default="{ row }">
                <el-tag size="small">{{ { MANUAL: '手动', WEBHOOK: 'Webhook' }[row.triggerType] || row.triggerType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
                  {{ row.status === 'ENABLED' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stageCount" label="阶段数" width="80" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link :data-testid="`pipeline-run-${row.id}`" @click="run(row)">触发构建</el-button>
                <el-button size="small" link @click="$router.push(`/applications/${appId}/pipelines/${row.id}`)">编排</el-button>
                <el-button size="small" type="danger" link :data-testid="`pipeline-delete-${row.id}`" @click="removePipeline(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="构建历史">
          <el-table :data="runs" data-testid="app-run-table">
            <el-table-column prop="version" label="版本" min-width="200" />
            <el-table-column prop="pipelineName" label="流水线" width="160" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="runType(row.status)">{{ runLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="triggerUserName" label="触发人" width="100" />
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">{{ fmtDuration(row.durationMs) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="$router.push(`/runs/${row.id}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="制品">
          <el-table :data="artifacts">
            <el-table-column prop="version" label="版本" min-width="200" />
            <el-table-column prop="type" label="类型" width="90" />
            <el-table-column prop="path" label="制品路径" min-width="240" />
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ fmtSize(row.sizeBytes) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="部署历史">
          <el-table :data="deployments">
            <el-table-column prop="version" label="版本" min-width="180" />
            <el-table-column prop="environmentName" label="环境" width="120" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.deployType === 'ROLLBACK' ? 'warning' : 'primary'">
                  {{ row.deployType === 'ROLLBACK' ? '回滚' : '部署' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="runType(row.status)">{{ runLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="$router.push('/deployments')">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="createPipelineDialog" title="新建流水线" width="480px">
      <el-form :model="pipelineForm" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="pipelineForm.name" data-testid="pipeline-name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="pipelineForm.description" type="textarea" /></el-form-item>
        <el-form-item label="触发方式">
          <el-radio-group v-model="pipelineForm.triggerType">
            <el-radio value="MANUAL">手动</el-radio>
            <el-radio value="WEBHOOK">Webhook</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createPipelineDialog = false">取消</el-button>
        <el-button type="primary" @click="createPipeline" data-testid="pipeline-save">创建并编排</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  createPipeline as apiCreatePipeline,
  deletePipeline,
  getApplication,
  listArtifacts,
  listDeployments,
  listPipelines,
  listRuns,
  triggerPipeline,
} from '../api'

const route = useRoute()
const router = useRouter()
const appId = computed(() => route.params.id)
const app = ref({})
const pipelines = ref([])
const runs = ref([])
const artifacts = ref([])
const deployments = ref([])
const createPipelineDialog = ref(false)
const pipelineForm = reactive({ name: '', description: '', triggerType: 'MANUAL' })

const runLabel = (s) => ({ PENDING: '排队中', RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', CANCELED: '已取消', DEPLOYING: '部署中' }[s] || s)
const runType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', DEPLOYING: 'warning' }[s] || 'info')
const fmtDuration = (ms) => (ms ? `${(ms / 1000).toFixed(1)}s` : '-')
const fmtSize = (b) => (b ? `${(b / 1024 / 1024).toFixed(1)} MB` : '-')

async function load() {
  app.value = (await getApplication(appId.value)).data
  pipelines.value = (await listPipelines(appId.value)).data
  runs.value = (await listRuns({ applicationId: appId.value, size: 20 })).data.records
  artifacts.value = (await listArtifacts({ applicationId: appId.value, size: 20 })).data.records
  deployments.value = (await listDeployments({ applicationId: appId.value, size: 20 })).data.records
}

async function createPipeline() {
  if (!pipelineForm.name) {
    ElMessage.warning('请填写名称')
    return
  }
  // 后端要求流水线至少一个阶段：创建时给默认骨架，进编排器后再调整
  const resp = await apiCreatePipeline(appId.value, {
    ...pipelineForm,
    stages: [{ name: '阶段 1', jobs: [{ name: 'Shell 任务', type: 'SHELL', config: '{"script":""}', continueOnFail: 0 }] }],
  })
  createPipelineDialog.value = false
  pipelineForm.name = ''
  pipelineForm.description = ''
  await load()
  router.push(`/applications/${appId.value}/pipelines/${resp.data.id}`)
}

async function run(row) {
  const resp = await triggerPipeline(row.id)
  ElMessage.success('已触发构建')
  router.push(`/runs/${resp.data.id}`)
}

async function removePipeline(row) {
  await ElMessageBox.confirm(`确认删除流水线「${row.name}」？`, '提示', { type: 'warning' })
  await deletePipeline(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.app-detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; padding: 12px 16px; }
.ad-avatar { background: #0fc6c2; }
.ad-meta { flex: 1; display: flex; flex-direction: column; }
.ad-name { font-size: 16px; font-weight: 600; color: var(--df-text); }
.ad-sub { color: var(--df-text-3); font-size: var(--df-font-sm); margin-top: 2px; }
</style>
