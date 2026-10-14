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

    <div class="proj-body">
      <aside class="proj-nav">
        <template v-for="g in navGroups" :key="g.title">
          <div class="pn-group">{{ g.title }}</div>
          <button
            v-for="item in g.items"
            :key="item.name"
            class="pn-item"
            :class="{ active: tab === item.name }"
            :data-testid="`proj-nav-${item.name}`"
            @click="tab = item.name"
          >
            {{ item.label }}
          </button>
        </template>
      </aside>
      <div class="proj-content">
        <WorkItemList v-if="tab === 'story'" :project-id="pid" type="STORY" />
        <WorkItemList v-else-if="tab === 'task'" :project-id="pid" type="TASK" />
        <WorkItemList v-else-if="tab === 'bug'" :project-id="pid" type="BUG" />
        <Board v-else-if="tab === 'board'" :project-id="pid" />
        <BacklogPanel v-else-if="tab === 'backlog'" :project-id="pid" />
        <IterationPanel v-else-if="tab === 'iteration'" :project-id="pid" />
        <ReleasePanel v-else-if="tab === 'release'" :project-id="pid" />
        <TestCasePanel v-else-if="tab === 'testcase'" :project-id="pid" />
        <TestPlanPanel v-else-if="tab === 'testplan'" :project-id="pid" />
        <BugReportPanel v-else-if="tab === 'report'" :project-id="pid" />
        <MemberPanel v-else-if="tab === 'member'" :project-id="pid" />
        <WorkflowSettings v-else-if="tab === 'settings'" :project-id="pid" />
      </div>
    </div>
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

const navGroups = [
  { title: '工作项', items: [
    { name: 'story', label: '需求' },
    { name: 'task', label: '任务' },
    { name: 'bug', label: '缺陷' },
  ]},
  { title: '规划', items: [
    { name: 'board', label: '看板' },
    { name: 'backlog', label: '待规划' },
    { name: 'iteration', label: '迭代' },
    { name: 'release', label: '发布' },
  ]},
  { title: '测试', items: [
    { name: 'testcase', label: '测试用例' },
    { name: 'testplan', label: '测试计划' },
  ]},
  { title: '报表', items: [
    { name: 'report', label: '缺陷报表' },
  ]},
  { title: '设置', items: [
    { name: 'member', label: '成员' },
    { name: 'settings', label: '项目设置' },
  ]},
]

const route = useRoute()
const router = useRouter()
const pid = computed(() => route.params.id)
const TABS = ['story', 'task', 'bug', 'board', 'backlog', 'iteration', 'testcase', 'testplan', 'release', 'report', 'member', 'settings']
const tab = ref(TABS.includes(String(route.name)) ? route.name : 'story')
const project = ref({})

// tab ⇄ 子路由双向同步：直接访问子路由 URL 时激活对应导航，切导航时更新 URL
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
.proj-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; padding: 12px 16px; }
.proj-head-meta { display: flex; flex-direction: column; }
.proj-name { font-size: 16px; font-weight: 600; color: var(--df-text); }
.proj-sub { color: var(--df-text-3); font-size: var(--df-font-sm); margin-top: 2px; }

.proj-body { display: flex; gap: 16px; align-items: flex-start; }
.proj-nav {
  width: 168px; flex-shrink: 0; background: var(--df-surface);
  border: 1px solid var(--df-border-light); border-radius: var(--df-radius);
  padding: 10px 8px;
}
.pn-group { font-size: 11px; color: var(--df-text-3); padding: 10px 10px 4px; letter-spacing: .5px; }
.pn-group:first-child { padding-top: 4px; }
.pn-item {
  display: block; width: 100%; text-align: left; border: none; background: transparent;
  height: 36px; line-height: 36px; padding: 0 12px; border-radius: var(--df-radius-sm);
  color: var(--df-text-2); font-size: var(--df-font); cursor: pointer;
}
.pn-item:hover { background: var(--df-sidebar-hover); color: var(--df-text); }
.pn-item.active { background: var(--df-sidebar-active); color: var(--df-primary); font-weight: 600; }
.proj-content { flex: 1; min-width: 0; }
</style>
