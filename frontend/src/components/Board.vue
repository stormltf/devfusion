<template>
  <div>
    <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center">
      <el-radio-group v-model="type" @change="load">
        <el-radio-button value="STORY">需求</el-radio-button>
        <el-radio-button value="TASK">任务</el-radio-button>
        <el-radio-button value="BUG">缺陷</el-radio-button>
      </el-radio-group>
      <span style="color: #999; font-size: 12px">拖动卡片到目标列即可流转状态</span>
    </div>
    <div class="board">
      <div v-for="col in columns" :key="col.code" class="board-col">
        <div class="board-col-head">
          <span>{{ col.name }}</span>
          <el-tag size="small" effect="plain">{{ col.items.length }}</el-tag>
        </div>
        <div
          class="board-col-body"
          :class="{ 'drag-over': dragOver === col.code }"
          @dragover.prevent="dragOver = col.code"
          @dragleave="dragOver = null"
          @drop="onDrop($event, col)"
          :data-testid="`board-col-${col.code}`"
        >
          <div
            v-for="card in col.items"
            :key="card.id"
            class="board-card"
            draggable="true"
            :data-testid="`board-card-${card.id}`"
            @dragstart="dragging = card"
            @dragend="dragging = null"
          >
            <div style="display: flex; justify-content: space-between; gap: 6px">
              <span style="color: var(--df-primary); font-size: 12px">{{ card.itemNo }}</span>
              <el-tag size="small" :type="priorityType(card.priority)" effect="plain">
                {{ priorityLabel(card.priority) }}
              </el-tag>
            </div>
            <div class="board-card-title">{{ card.title }}</div>
            <div style="color: #999; font-size: 12px">{{ card.assigneeName || '未指派' }}</div>
          </div>
          <div v-if="!col.items.length" class="board-empty">拖到这里</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBoard, transitionItem } from '../api'

const props = defineProps({ projectId: String })
const columns = ref([])
const type = ref('STORY')
const dragging = ref(null)
const dragOver = ref(null)

const priorityLabel = (p) => ({ URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p)
const priorityType = (p) => ({ URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }[p] || 'info')

async function load() {
  const resp = await getBoard(props.projectId, type.value)
  columns.value = resp.data.columns
}

async function onDrop(_evt, col) {
  dragOver.value = null
  const card = dragging.value
  if (!card || !col) return
  const fromCol = columns.value.find((c) => c.items.some((i) => i.id === card.id))
  if (!fromCol || fromCol.code === col.code) return
  await transitionItem(card.id, col.code)
  ElMessage.success(`${card.itemNo} → ${col.name}`)
  await load()
}

onMounted(load)
defineExpose({ load })
</script>

<style scoped>
.board { display: flex; gap: 12px; align-items: flex-start; }
.board-col { flex: 1; min-width: 220px; background: #f4f6fa; border-radius: var(--df-radius); padding: 8px; }
.board-col-head { display: flex; justify-content: space-between; align-items: center; padding: 4px 6px 8px; font-weight: 600; font-size: var(--df-font); color: var(--df-text); }
.board-col-body { min-height: 200px; display: flex; flex-direction: column; gap: 8px; border-radius: 6px; }
.board-col-body.drag-over { outline: 2px dashed var(--df-primary); background: var(--df-primary-light); }
.board-card { background: #fff; border-radius: var(--df-radius-sm); padding: 10px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); cursor: grab; }
.board-card:active { cursor: grabbing; }
.board-card-title { margin: 6px 0; font-size: 13px; line-height: 1.5; }
.board-empty { color: #bbb; font-size: 12px; text-align: center; padding: 24px 0; }
</style>
