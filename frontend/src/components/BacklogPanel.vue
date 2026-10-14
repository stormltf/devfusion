<template>
  <div>
    <div class="toolbar">
      <el-radio-group v-model="type" size="small" @change="load" data-testid="backlog-type">
        <el-radio-button value="STORY">需求</el-radio-button>
        <el-radio-button value="TASK">任务</el-radio-button>
        <el-radio-button value="BUG">缺陷</el-radio-button>
      </el-radio-group>
    </div>
    <el-alert
      v-if="loadError"
      type="error" show-icon :closable="false" title="加载失败" style="margin-bottom: 12px"
    >
      <el-button size="small" type="primary" @click="load" data-testid="retry-load">重试</el-button>
    </el-alert>
    <div class="backlog-layout" v-loading="loading">
      <div
        class="column"
        data-testid="backlog-unplanned"
        @dragover.prevent
        @drop="onDrop(null)"
      >
        <div class="column-head">
          <b>待规划</b>
          <el-tag size="small" type="info">{{ unplanned.length }}</el-tag>
        </div>
        <div
          v-for="it in unplanned"
          :key="it.id"
          class="card"
          draggable="true"
          :data-testid="`backlog-item-${it.id}`"
          @dragstart="onDragStart($event, it)"
        >
          <span class="item-no">{{ it.itemNo }}</span>
          <span class="item-title">{{ it.title }}</span>
          <PriorityTag :value="it.priority" />
        </div>
        <div v-if="!unplanned.length" class="empty">拖拽工作项到右侧迭代即可规划</div>
      </div>

      <div
        v-for="it in iterations"
        :key="it.id"
        class="column planned"
        :data-testid="`backlog-iter-${it.id}`"
        @dragover.prevent
        @drop="onDrop(it.id)"
      >
        <div class="column-head">
          <b>{{ it.name }}</b>
          <el-tag size="small" :type="iterType(it.status)">{{ iterLabel(it.status) }}</el-tag>
        </div>
        <div
          v-for="c in planned[it.id] || []"
          :key="c.id"
          class="card"
          draggable="true"
          :data-testid="`backlog-item-${c.id}`"
          @dragstart="onDragStart($event, c)"
        >
          <span class="item-no">{{ c.itemNo }}</span>
          <span class="item-title">{{ c.title }}</span>
          <el-button link type="danger" size="small" @click="confirmUnplan(c)" :data-testid="`backlog-remove-${c.id}`">移出</el-button>
        </div>
        <div v-if="!(planned[it.id] || []).length" class="empty">拖到这里规划</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBacklog, listIterations, listItems, planBacklog, unplanBacklog } from '../api'
import PriorityTag from './common/PriorityTag.vue'
import { ITERATION_STATUS } from '../utils/enums'

const props = defineProps({ projectId: String })
const type = ref('STORY')
const unplanned = ref([])
const iterations = ref([])
const planned = ref({})
const loading = ref(false)
const loadError = ref(false)
let dragItem = null

const iterLabel = (s) => ITERATION_STATUS[s]?.label || s
const iterType = (s) => ITERATION_STATUS[s]?.type || 'info'

function onDragStart(e, item) {
  dragItem = item
  e.dataTransfer.setData('text/plain', String(item.id))
}

async function onDrop(iterationId) {
  if (!dragItem) return
  if (iterationId == null) {
    await unplan(dragItem, false)
  } else if (dragItem.iterationId !== iterationId) {
    await planBacklog(props.projectId, { itemIds: [dragItem.id], iterationId })
    ElMessage.success('已规划入迭代')
  }
  dragItem = null
  await load()
}

async function confirmUnplan(item) {
  await ElMessageBox.confirm(`确认将「${item.itemNo}」移出当前迭代？`, '提示', { type: 'warning' })
  await unplan(item)
}

async function unplan(item, reload = true) {
  await unplanBacklog(props.projectId, { itemIds: [item.id] })
  ElMessage.success('已移出迭代')
  if (reload) await load()
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [un, its] = await Promise.all([
      listBacklog(props.projectId, { type: type.value, size: 100 }),
      listIterations(props.projectId),
    ])
    unplanned.value = un.data.records
    iterations.value = its.data
    planned.value = {}
    for (const it of its.data) {
      const resp = await listItems(props.projectId, { iterationId: it.id, size: 100 })
      planned.value[it.id] = resp.data.records
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.backlog-layout { display: flex; gap: 12px; align-items: flex-start; }
.column { flex: 1; min-width: 220px; background: var(--df-surface); border-radius: var(--df-radius); padding: 12px; min-height: 300px; }
.column.planned { background: var(--df-bg); }
.column-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: var(--df-font); }
.card { display: flex; align-items: center; gap: 8px; background: var(--df-surface); border: 1px solid var(--df-border-light); border-radius: var(--df-radius-sm); padding: 8px 10px; margin-bottom: 8px; cursor: grab; }
.card:hover { border-color: var(--df-primary); }
.item-no { color: var(--df-primary); font-size: var(--df-font-sm); white-space: nowrap; }
.item-title { flex: 1; font-size: var(--df-font); color: var(--df-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { color: var(--df-text-3); font-size: var(--df-font-sm); text-align: center; padding: 20px 0; }
</style>
