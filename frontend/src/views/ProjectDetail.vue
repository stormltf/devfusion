<template>
  <div>
    <div class="proj-head page-card">
      <el-button :icon="ArrowLeft" circle plain size="small" @click="$router.push('/projects')" />
      <el-avatar :size="36" :style="{ background: project.color || '#0064ff' }">{{ (project.name || '?').slice(0, 1) }}</el-avatar>
      <div class="proj-head-meta">
        <span class="proj-name">{{ project.name }}</span>
        <span class="proj-sub">{{ project.key }} · {{ project.memberCount || 0 }} 名成员</span>
      </div>
    </div>
    <el-tabs v-model="tab" class="proj-tabs">
      <el-tab-pane label="需求" name="story"><WorkItemList v-if="tab === 'story'" :project-id="pid" type="STORY" /></el-tab-pane>
      <el-tab-pane label="任务" name="task"><WorkItemList v-if="tab === 'task'" :project-id="pid" type="TASK" /></el-tab-pane>
      <el-tab-pane label="缺陷" name="bug"><WorkItemList v-if="tab === 'bug'" :project-id="pid" type="BUG" /></el-tab-pane>
      <el-tab-pane label="看板" name="board"><Board v-if="tab === 'board'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="Backlog" name="backlog"><BacklogPanel v-if="tab === 'backlog'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="迭代" name="iteration"><IterationPanel v-if="tab === 'iteration'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="测试用例" name="testcase"><TestCasePanel v-if="tab === 'testcase'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="测试计划" name="testplan"><TestPlanPanel v-if="tab === 'testplan'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="发布" name="release"><ReleasePanel v-if="tab === 'release'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="缺陷报表" name="report"><BugReportPanel v-if="tab === 'report'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="成员" name="member"><MemberPanel v-if="tab === 'member'" :project-id="pid" /></el-tab-pane>
      <el-tab-pane label="项目设置" name="settings"><WorkflowSettings v-if="tab === 'settings'" :project-id="pid" /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getProject } from '../api'
import WorkItemList from '../components/WorkItemList.vue'
import Board from '../components/Board.vue'
import BacklogPanel from '../components/BacklogPanel.vue'
import IterationPanel from '../components/IterationPanel.vue'
import TestCasePanel from '../components/TestCasePanel.vue'
import TestPlanPanel from '../components/TestPlanPanel.vue'
import ReleasePanel from '../components/ReleasePanel.vue'
import BugReportPanel from '../components/BugReportPanel.vue'
import MemberPanel from '../components/MemberPanel.vue'
import WorkflowSettings from '../components/WorkflowSettings.vue'

const route = useRoute()
const router = useRouter()
const pid = computed(() => route.params.id)
const TABS = ['story', 'task', 'bug', 'board', 'backlog', 'iteration', 'testcase', 'testplan', 'release', 'report', 'member', 'settings']
const tab = ref(TABS.includes(String(route.name)) ? route.name : 'story')
const project = ref({})

// tab ⇄ 子路由双向同步：直接访问子路由 URL 时激活对应 tab，切 tab 时更新 URL
watch(() => route.name, (n) => {
  if (TABS.includes(String(n)) && n !== tab.value) tab.value = n
})
watch(tab, (t) => {
  if (route.name !== t) router.push(`/projects/${pid.value}/${t}`)
})

onMounted(async () => {
  const resp = await getProject(pid.value)
  project.value = resp.data
})
</script>

<style scoped>
.proj-head { display: flex; align-items: center; gap: 12px; margin-bottom: 4px; padding: 12px 16px; }
.proj-head-meta { display: flex; flex-direction: column; }
.proj-name { font-size: 16px; font-weight: 600; color: var(--df-text); }
.proj-sub { color: var(--df-text-3); font-size: var(--df-font-sm); margin-top: 2px; }

.proj-tabs { background: transparent; }
.proj-tabs :deep(.el-tabs__header) { margin: 0 0 14px; }
.proj-tabs :deep(.el-tabs__item) { font-size: var(--df-font); color: var(--df-text-2); }
.proj-tabs :deep(.el-tabs__item.is-active) { color: var(--df-primary); font-weight: 600; }
.proj-tabs :deep(.el-tabs__active-bar) { background: var(--df-primary); }
.proj-tabs :deep(.el-tabs__content) { overflow: visible; }
</style>
