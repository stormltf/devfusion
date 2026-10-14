<template>
  <el-drawer v-model="visible" size="520px" :title="item.itemNo || '详情'" @closed="$emit('close')">
    <template v-if="item.id">
      <h3 style="margin-bottom: 4px" data-testid="item-detail-title">{{ item.title }}</h3>
      <div style="color: #999; font-size: 12px; margin-bottom: 12px">
        {{ item.itemNo }} · 创建于 {{ fmtTime(item.createdAt) }}
      </div>

      <el-descriptions :column="2" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="状态" :data-testid="`detail-status-${item.statusCode}`">
          <el-select :model-value="item.statusCode" size="small" style="width: 120px" @change="doTransition" data-testid="detail-status-select">
            <el-option v-for="s in statuses" :key="s.code" :value="s.code" :label="s.name" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-select v-model="item.priority" size="small" style="width: 100px" @change="saveField('priority')">
            <el-option value="URGENT" label="紧急" />
            <el-option value="HIGH" label="高" />
            <el-option value="MEDIUM" label="中" />
            <el-option value="LOW" label="低" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">
          <el-select v-model="item.assigneeId" size="small" style="width: 120px" clearable @change="saveField('assigneeId')">
            <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" />
          </el-select>
        </el-descriptions-item>
        <el-descriptions-item label="迭代">
          <el-select v-model="item.iterationId" size="small" style="width: 140px" clearable @change="saveField('iterationId')">
            <el-option v-for="it in iterations" :key="it.id" :value="it.id" :label="it.name" />
          </el-select>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="tab">
        <el-tab-pane label="描述" name="desc">
          <p style="white-space: pre-wrap; color: #444; font-size: 13px; line-height: 1.8">
            {{ item.description || '暂无描述' }}
          </p>
        </el-tab-pane>
        <el-tab-pane :label="`评论 (${comments.length})`" name="comments">
          <div v-for="c in comments" :key="c.id" class="comment">
            <b>{{ c.nickname }}</b>
            <span style="color: #bbb; font-size: 12px; margin-left: 8px">{{ fmtTime(c.createdAt) }}</span>
            <p>{{ c.content }}</p>
          </div>
          <div style="display: flex; gap: 8px; margin-top: 8px">
            <el-input v-model="commentText" placeholder="写下评论…" data-testid="comment-input" @keyup.enter="sendComment" />
            <el-button type="primary" @click="sendComment" data-testid="comment-submit">发送</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="动态" name="activities">
          <el-timeline style="padding-left: 4px">
            <el-timeline-item v-for="a in activities" :key="a.id" :timestamp="fmtTime(a.createdAt)">
              <b>{{ a.nickname }}</b>
              {{ actionText(a) }}
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
        <el-tab-pane :label="`子项 (${(item.children || []).length})`" name="children">
          <el-table :data="item.children || []" size="small">
            <el-table-column prop="itemNo" label="编号" width="140" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.statusName || row.statusCode }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`工时 (${worklogs.length})`" name="worklogs">
          <div class="wl-summary">
            预估 {{ item.estimateHours ?? 0 }}h · 已耗 {{ item.spentHours ?? 0 }}h · 剩余 {{ item.remainingHours ?? 0 }}h
          </div>
          <el-table :data="worklogs" size="small" data-testid="worklog-table">
            <el-table-column prop="nickname" label="记录人" width="110" />
            <el-table-column prop="hours" label="工时(h)" width="90" />
            <el-table-column prop="remaining" label="剩余(h)" width="90" />
            <el-table-column prop="logDate" label="日期" width="110" />
            <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
          </el-table>
          <div class="wl-add">
            <el-input v-model="wlForm.hours" placeholder="工时h" size="small" style="width: 90px" data-testid="worklog-hours" />
            <el-input v-model="wlForm.remaining" placeholder="剩余h" size="small" style="width: 90px" />
            <el-date-picker v-model="wlForm.logDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 130px" />
            <el-input v-model="wlForm.note" placeholder="备注" size="small" style="width: 160px" />
            <el-button type="primary" size="small" @click="addWl" data-testid="worklog-add">登记</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addComment,
  addWorklog,
  getItem,
  listActivities,
  listComments,
  listIterations,
  listSimpleUsers,
  listStatuses,
  listWorklogs,
  transitionItem,
  updateItem,
} from '../api'

const props = defineProps({ itemId: String })
const emit = defineEmits(['close', 'changed'])

const visible = ref(true)
const item = ref({})
const statuses = ref([])
const users = ref([])
const iterations = ref([])
const comments = ref([])
const activities = ref([])
const worklogs = ref([])
const wlForm = reactive({ hours: '', remaining: '', logDate: '', note: '' })
const commentText = ref('')
const tab = ref('desc')

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

function actionText(a) {
  const d = a.detail ? JSON.parse(a.detail) : {}
  switch (a.action) {
    case 'CREATE':
      return `创建了该${{ STORY: '需求', TASK: '任务', BUG: '缺陷' }[item.value.type] || '工作项'}`
    case 'STATUS':
      return `将状态从「${d.from}」流转为「${d.to}」`
    case 'ASSIGN':
      return '变更了负责人'
    case 'UPDATE':
      return `修改了 ${d.fields || '字段'}`
    case 'COMMENT':
      return '发表了评论'
    default:
      return a.action
  }
}

async function loadAll() {
  const [it, st, us, iter, cm, act, wl] = await Promise.all([
    getItem(props.itemId),
    listStatuses(itemProjectId()),
    listSimpleUsers(),
    listIterations(itemProjectId()),
    listComments(props.itemId),
    listActivities(props.itemId),
    listWorklogs(props.itemId),
  ])
  item.value = it.data
  statuses.value = (st.data || []).filter((s) => s.type === it.data.type)
  users.value = us.data
  iterations.value = iter.data
  comments.value = cm.data
  activities.value = act.data
  worklogs.value = wl.data
}

function itemProjectId() {
  return item.value.projectId || routeProjectId()
}

function routeProjectId() {
  return window.location.pathname.split('/')[2]
}

async function doTransition(code) {
  await transitionItem(props.itemId, code)
  ElMessage.success('状态已流转')
  await loadAll()
  emit('changed')
}

async function saveField(field) {
  await updateItem(props.itemId, { [field]: item.value[field] })
  ElMessage.success('已更新')
  await loadAll()
  emit('changed')
}

async function sendComment() {
  if (!commentText.value.trim()) return
  await addComment(props.itemId, commentText.value)
  commentText.value = ''
  comments.value = (await listComments(props.itemId)).data
  activities.value = (await listActivities(props.itemId)).data
  emit('changed')
}

async function addWl() {
  if (!wlForm.hours) {
    ElMessage.warning('请填写工时')
    return
  }
  await addWorklog(props.itemId, {
    hours: Number(wlForm.hours),
    remaining: wlForm.remaining ? Number(wlForm.remaining) : undefined,
    logDate: wlForm.logDate || undefined,
    note: wlForm.note || undefined,
  })
  ElMessage.success('已登记工时')
  wlForm.hours = ''
  wlForm.remaining = ''
  wlForm.note = ''
  await loadAll()
}

watch(() => props.itemId, loadAll, { immediate: true })
</script>

<style scoped>
.comment { border-bottom: 1px solid #f0f0f0; padding: 8px 0; font-size: 13px; }
.comment p { margin-top: 4px; color: #444; }
.wl-summary { font-size: 13px; color: #666; margin-bottom: 8px; }
.wl-add { display: flex; gap: 6px; margin-top: 10px; align-items: center; }
</style>
