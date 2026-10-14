<template>
  <div>
    <div class="page-card run-head">
      <el-button :icon="ArrowLeft" circle plain size="small" @click="$router.back()" />
      <b class="run-version" data-testid="run-version">{{ run.version }}</b>
      <el-tag :type="runType(run.status)" data-testid="run-status">{{ runLabel(run.status) }}</el-tag>
      <span class="run-meta">
        触发方式：{{ { MANUAL: '手动', WEBHOOK: 'Webhook' }[run.triggerType] || run.triggerType }}
        <template v-if="run.triggerUserName">（{{ run.triggerUserName }}）</template>
        <template v-if="run.durationMs"> · 耗时 {{ (run.durationMs / 1000).toFixed(1) }}s</template>
      </span>
      <div style="flex: 1" />
      <el-button
        v-if="run.status === 'RUNNING' || run.status === 'PENDING'"
        type="danger"
        plain
        :data-testid="`run-cancel`"
        @click="cancel"
      >取消构建</el-button>
      <el-button :icon="Refresh" circle plain size="small" @click="load" />
    </div>

    <div class="run-stages">
      <div v-for="(stage, si) in run.stages || []" :key="stage.id" class="run-stage" :data-testid="`run-stage-${si}`">
        <div class="run-stage-head" :class="stageCls(stage.status)">
          <span>{{ stage.name }}</span>
          <el-tag size="small" :type="runType(stage.status)">{{ runLabel(stage.status) }}</el-tag>
        </div>
        <div v-for="(job, ji) in stage.jobs" :key="job.id" class="run-job" :data-testid="`run-job-${si}-${ji}`">
          <div class="run-job-head" @click="activeJob = activeJob === job.id ? null : job.id">
            <el-tag size="small" :type="jobDot(job.status)">{{ jobDotText(job.status) }}</el-tag>
            <span class="run-job-name">{{ job.name }}</span>
            <span class="run-job-type">{{ jobTypeLabel(job.type) }}</span>
            <div style="flex: 1" />
            <span class="run-job-dur">{{ jobDuration(job) }}</span>
            <el-button
              v-if="job.status === 'WAITING_APPROVAL'"
              size="small" type="primary" link :data-testid="`job-approve-${job.id}`"
              @click.stop="approve(job)">通过</el-button>
            <el-button
              v-if="job.status === 'WAITING_APPROVAL'"
              size="small" type="danger" link :data-testid="`job-reject-${job.id}`"
              @click.stop="reject(job)">拒绝</el-button>
            <el-icon><ArrowDown v-if="activeJob === job.id" /><ArrowRight v-else /></el-icon>
          </div>
          <pre v-if="activeJob === job.id" class="run-log" :data-testid="`run-log-${si}-${ji}`">{{ job.log || '等待日志…' }}</pre>
        </div>
      </div>
    </div>

    <div v-if="workItems.length" class="page-card" style="margin-top: 16px">
      <div style="font-weight: 600; margin-bottom: 8px">关联工作项</div>
      <el-tag v-for="w in workItems" :key="w.workItemNo" size="small" effect="plain" style="margin-right: 6px" data-testid="run-workitem">
        {{ w.workItemNo }}
      </el-tag>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowRight, Refresh } from '@element-plus/icons-vue'
import { cancelRun, getRun, approveRunJob, rejectRunJob, getRunWorkItems } from '../api'

const route = useRoute()
const runId = computed(() => route.params.id)
const run = ref({})
const activeJob = ref(null)
const workItems = ref([])
let timer = null

const runLabel = (s) => ({ PENDING: '排队中', RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', CANCELED: '已取消', SKIPPED: '跳过', WAITING_APPROVAL: '待审批' }[s] || s)
const runType = (s) => ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', WAITING_APPROVAL: 'warning' }[s] || 'info')
const jobTypeLabel = (t) =>
  ({ SHELL: 'Shell', MAVEN_BUILD: 'Maven', NPM_BUILD: 'NPM', DOCKER_BUILD: 'Docker', UNIT_TEST: '单测', ARTIFACT_UPLOAD: '制品', APPROVAL: '人工审批', TRIGGER_PIPELINE: '子流水线' }[t] || t)
const jobDot = (s) => ({ SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning', SKIPPED: 'info', CANCELED: 'info', WAITING_APPROVAL: 'warning' }[s] || 'info')
const jobDotText = (s) => ({ SUCCESS: '✓', FAILED: '✕', RUNNING: '…', SKIPPED: '—', CANCELED: '⊘', WAITING_APPROVAL: '✋' }[s] || '…')
const stageCls = (s) => ({ SUCCESS: 'ok', FAILED: 'bad', RUNNING: 'busy' }[s] || '')
const jobDuration = (j) => (j.startedAt && j.finishedAt ? `${((new Date(j.finishedAt) - new Date(j.startedAt)) / 1000).toFixed(1)}s` : '')

async function load() {
  const resp = await getRun(runId.value)
  run.value = resp.data
  getRunWorkItems(runId.value).then((r) => (workItems.value = r.data || [])).catch(() => {})
  // 默认展开最后一个运行中的/第一个 job 的日志
  if (!activeJob.value) {
    for (const s of run.value.stages || []) {
      for (const j of s.jobs || []) {
        if (j.status === 'RUNNING') {
          activeJob.value = j.id
          return
        }
      }
    }
    if (run.value.stages?.[0]?.jobs?.length) activeJob.value = run.value.stages[0].jobs[0].id
  }
}

async function cancel() {
  await ElMessageBox.confirm('确认取消本次构建？', '取消构建', { type: 'warning' })
  await cancelRun(runId.value)
  ElMessage.success('已发送取消请求')
  load()
}

async function approve(job) {
  await ElMessageBox.confirm(`确认通过「${job.name}」人工审批？`, '审批确认', { type: 'warning' })
  await approveRunJob(runId.value, job.id)
  ElMessage.success('已审批通过')
  load()
}

async function reject(job) {
  await ElMessageBox.confirm(`确认拒绝「${job.name}」？拒绝后流水线将终止。`, '拒绝确认', { type: 'warning' })
  await rejectRunJob(runId.value, job.id)
  ElMessage.success('已拒绝')
  load()
}

onMounted(() => {
  load()
  timer = setInterval(load, 2000)
})

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.run-head { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; padding: 12px 16px; }
.run-version { font-size: 15px; color: var(--df-text); }
.run-meta { color: var(--df-text-3); font-size: var(--df-font-sm); }
.run-stages { display: flex; gap: 12px; align-items: flex-start; overflow-x: auto; }
.run-stage { min-width: 320px; flex: 1; }
.run-stage-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: var(--df-surface); border: 1px solid var(--df-border-light); border-radius: var(--df-radius) var(--df-radius) 0 0; font-weight: 600; color: var(--df-text); }
.run-stage-head.ok { background: #e8f7ec; }
.run-stage-head.bad { background: #feeceb; }
.run-stage-head.busy { background: #fef3e8; }
.run-job { background: var(--df-surface); border: 1px solid var(--df-border-light); border-top: none; }
.run-job:last-child { border-radius: 0 0 var(--df-radius) var(--df-radius); }
.run-job-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; font-size: var(--df-font); }
.run-job-name { font-weight: 600; color: var(--df-text); }
.run-job-type { color: var(--df-text-3); font-size: var(--df-font-sm); }
.run-job-dur { color: #b8bcc4; font-size: var(--df-font-sm); }
.run-log { margin: 0; padding: 12px; background: #161c2d; color: #c8e1ff; font-size: var(--df-font-sm); line-height: 1.7; max-height: 320px; overflow: auto; white-space: pre-wrap; }
</style>
