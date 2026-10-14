<template>
  <div>
    <div class="page-card editor-head">
      <el-button :icon="ArrowLeft" circle plain size="small" @click="$router.push(`/applications/${appId}`)" />
      <b class="editor-title">{{ pipeline.name || '流水线编排' }}</b>
      <el-select v-model="pipeline.triggerType" style="width: 120px">
        <el-option value="MANUAL" label="手动触发" />
        <el-option value="WEBHOOK" label="Webhook 触发" />
      </el-select>
      <el-tag v-if="pipeline.webhookToken" type="info" effect="plain" size="small">
        token: {{ pipeline.webhookToken.slice(0, 12) }}…
      </el-tag>
      <div style="flex: 1" />
      <el-button @click="addStage" data-testid="stage-add">添加阶段</el-button>
      <el-button type="primary" :loading="saving" @click="save" data-testid="pipeline-save-editor">保存编排</el-button>
      <el-button type="success" @click="run" data-testid="pipeline-run-editor">保存并触发</el-button>
    </div>

    <div class="stage-strip">
      <div v-for="(stage, si) in stages" :key="si" class="stage-box">
        <div class="stage-head">
          <el-input v-model="stage.name" size="small" style="flex: 1" placeholder="阶段名" :data-testid="`stage-name-${si}`" />
          <el-button size="small" text type="danger" @click="removeStage(si)">✕</el-button>
        </div>
        <div class="stage-jobs">
          <div v-for="(job, ji) in stage.jobs" :key="ji" class="job-card">
            <div style="display: flex; gap: 6px; align-items: center">
              <el-tag size="small" :type="jobTypeColor(job.type)">{{ job.type }}</el-tag>
              <el-input v-model="job.name" size="small" placeholder="任务名" style="flex: 1" :data-testid="`job-name-${si}-${ji}`" />
              <el-button size="small" text type="danger" @click="stage.jobs.splice(ji, 1)">✕</el-button>
            </div>
            <el-select v-model="job.type" size="small" style="margin-top: 6px; width: 100%">
              <el-option v-for="t in jobTypes" :key="t" :value="t" :label="jobTypeLabel(t)" />
            </el-select>
            <el-input
              v-if="job.type === 'SHELL'"
              v-model="job.configObj.script"
              type="textarea"
              :rows="3"
              placeholder="shell 脚本（exit 1 可模拟失败）"
              style="margin-top: 6px"
              :data-testid="`job-script-${si}-${ji}`"
            />
            <div v-else-if="job.type === 'UNIT_TEST'" style="margin-top: 6px; display: flex; gap: 6px; align-items: center">
              <span style="font-size: 12px; color: var(--df-text-3)">用例数</span>
              <el-input-number v-model="job.configObj.testCases" size="small" :min="1" style="flex: 1" />
              <span style="font-size: 12px; color: var(--df-text-3)">门禁%</span>
              <el-input-number v-model="job.configObj.passRateThreshold" size="small" :min="0" :max="100" style="flex: 1" placeholder="如 95" />
            </div>
            <div v-else-if="job.type === 'MAVEN_BUILD'" style="margin-top: 6px">
              <el-input v-model="job.configObj.goals" size="small" placeholder="mvn goals，如 clean package" />
            </div>
            <div v-else-if="job.type === 'DOCKER_BUILD'" style="margin-top: 6px">
              <el-input v-model="job.configObj.tag" size="small" placeholder="镜像 tag" />
            </div>
            <div v-else-if="job.type === 'TRIGGER_PIPELINE'" style="margin-top: 6px">
              <el-input v-model="job.configObj.pipelineId" size="small" placeholder="目标流水线 ID" />
              <el-checkbox v-model="job.configObj.wait" size="small" style="margin-top: 4px">等待子流水线结果</el-checkbox>
            </div>
            <div v-else-if="job.type === 'APPROVAL'" style="margin-top: 6px; color: var(--df-text-3); font-size: 12px">
              执行到此任务时挂起，等待人工审批通过后继续。
            </div>
            <el-checkbox v-model="job.continueOnFailBool" size="small" style="margin-top: 4px">失败继续</el-checkbox>
            <el-checkbox v-model="job.parallelBool" size="small" style="margin-top: 4px; margin-left: 8px">同阶段并行</el-checkbox>
            <el-input-number v-model="job.timeoutMinutes" size="small" :min="0" :step="1" controls-position="right"
              placeholder="超时分钟" style="width: 120px; margin-top: 4px" />
          </div>
        </div>
        <el-button size="small" style="width: 100%; margin-top: 8px" :data-testid="`job-add-${si}`" @click="addJob(si)">+ 添加任务</el-button>
      </div>
    </div>

    <el-alert v-if="!stages.length" title="还没有阶段，点击「添加阶段」开始编排流水线" type="info" :closable="false" style="margin-top: 12px" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getPipeline, triggerPipeline, updatePipeline } from '../api'

const route = useRoute()
const router = useRouter()
const appId = computed(() => route.params.id)
const pipelineId = computed(() => route.params.pipelineId)
const pipeline = ref({})
const stages = ref([])
const saving = ref(false)

const jobTypes = ['SHELL', 'MAVEN_BUILD', 'NPM_BUILD', 'DOCKER_BUILD', 'UNIT_TEST', 'ARTIFACT_UPLOAD', 'APPROVAL', 'TRIGGER_PIPELINE']
const jobTypeLabel = (t) =>
  ({
    SHELL: 'Shell 脚本', MAVEN_BUILD: 'Maven 构建', NPM_BUILD: 'NPM 构建',
    DOCKER_BUILD: 'Docker 构建', UNIT_TEST: '单元测试', ARTIFACT_UPLOAD: '归档制品',
    APPROVAL: '人工审批', TRIGGER_PIPELINE: '子流水线触发',
  }[t] || t)
const jobTypeColor = (t) => ({ SHELL: 'info', MAVEN_BUILD: 'primary', NPM_BUILD: 'primary', DOCKER_BUILD: 'warning', UNIT_TEST: 'success', ARTIFACT_UPLOAD: 'danger', APPROVAL: 'warning', TRIGGER_PIPELINE: 'primary' }[t] || 'info')

function parseConfig(job) {
  let obj = {}
  try {
    obj = job.config ? JSON.parse(job.config) : {}
  } catch {
    obj = {}
  }
  return obj
}

async function load() {
  const resp = await getPipeline(pipelineId.value)
  pipeline.value = resp.data
  stages.value = (resp.data.stages || []).map((s) => ({
    name: s.name,
    jobs: s.jobs.map((j) => ({
      name: j.name,
      type: j.type,
      configObj: parseConfig(j),
      continueOnFailBool: j.continueOnFail === 1,
      parallelBool: j.parallel === 1,
      timeoutMinutes: j.timeoutMinutes || null,
    })),
  }))
}

function addStage() {
  stages.value.push({ name: `阶段 ${stages.value.length + 1}`, jobs: [] })
}

function removeStage(si) {
  stages.value.splice(si, 1)
}

function addJob(si) {
  stages.value[si].jobs.push({ name: '', type: 'SHELL', configObj: { script: '' }, continueOnFailBool: false, parallelBool: false, timeoutMinutes: null })
}

function toPayload() {
  return {
    name: pipeline.value.name,
    description: pipeline.value.description,
    triggerType: pipeline.value.triggerType,
    stages: stages.value.map((s) => ({
      name: s.name,
      jobs: s.jobs.map((j) => ({
        name: j.name || jobTypeLabel(j.type),
        type: j.type,
        config: JSON.stringify(j.configObj),
        continueOnFail: j.continueOnFailBool ? 1 : 0,
        parallel: j.parallelBool ? 1 : 0,
        timeoutMinutes: j.timeoutMinutes || null,
      })),
    })),
  }
}

async function save() {
  saving.value = true
  try {
    await updatePipeline(pipelineId.value, toPayload())
    ElMessage.success('编排已保存')
  } finally {
    saving.value = false
  }
}

async function run() {
  await updatePipeline(pipelineId.value, toPayload())
  const resp = await triggerPipeline(pipelineId.value)
  ElMessage.success('已触发构建')
  router.push(`/runs/${resp.data.id}`)
}

onMounted(load)
</script>

<style scoped>
.editor-head { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; padding: 12px 16px; }
.editor-title { font-size: 16px; color: var(--df-text); }
.stage-strip { display: flex; gap: 14px; align-items: flex-start; overflow-x: auto; padding-bottom: 8px; }
.stage-box { min-width: 260px; max-width: 300px; background: var(--df-surface); border-radius: var(--df-radius); padding: 12px; border: 1px solid var(--df-border-light); flex-shrink: 0; }
.stage-head { display: flex; gap: 6px; align-items: center; margin-bottom: 8px; }
.stage-jobs { display: flex; flex-direction: column; gap: 8px; }
.job-card { border: 1px solid var(--df-border-light); border-radius: var(--df-radius-sm); padding: 8px; background: #fafbfc; }
</style>
