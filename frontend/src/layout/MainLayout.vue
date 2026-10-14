<template>
  <el-container class="layout">
    <el-aside class="sidebar" :width="collapsed ? '64px' : '220px'">
      <div class="brand">
        <span class="logo-mark">D</span>
        <div v-show="!collapsed" class="logo">DevFusion</div>
      </div>
      <div class="side-scroll">
        <template v-for="(g, gi) in groups" :key="gi">
          <div v-show="!collapsed" class="group-title">{{ g.title }}</div>
          <el-menu :default-active="activeMenu" :collapse="collapsed" router class="side-menu">
            <el-menu-item
              v-for="m in g.items"
              v-show="!m.admin || auth.isAdmin"
              :key="m.index"
              :index="m.index"
              :data-testid="`nav-${m.index.slice(1)}`"
            >
              <el-icon><component :is="m.icon" /></el-icon>
              <span>{{ m.title }}</span>
            </el-menu-item>
          </el-menu>
        </template>
      </div>
      <div class="side-foot">
        <el-button text @click="collapsed = !collapsed" :data-testid="`nav-collapse`" :title="collapsed ? '展开' : '收起'">
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
        </el-button>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header" height="52px">
        <el-breadcrumb separator="/" class="crumb">
          <el-breadcrumb-item v-for="(c, i) in crumbs" :key="i" :to="c.to || undefined">{{ c.label }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="header-right">
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell" data-testid="notif-badge">
            <el-button text circle @click="openNotif" data-testid="notif-bell">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown @command="(cmd) => cmd === 'logout' && auth.logout()">
            <div class="user">
              <el-avatar :size="30" class="user-avatar">{{ auth.nickname.slice(0, 1) }}</el-avatar>
              <span v-show="true" class="user-name">{{ auth.nickname }}</span>
              <el-icon class="user-caret"><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="notifDialog" title="通知" width="480px" data-testid="notif-dialog">
      <div style="text-align: right; margin-bottom: 8px">
        <el-button size="small" @click="readAll" data-testid="notif-readall">全部已读</el-button>
      </div>
      <el-scrollbar height="420px">
        <div v-for="n in notifs" :key="n.id" class="notif-item" :class="{ unread: !n.readAt }" :data-testid="`notif-${n.id}`" @click="openNotifLink(n)">
          <div style="display: flex; justify-content: space-between">
            <b class="notif-title">{{ n.title }}</b>
            <el-button v-if="!n.readAt" size="small" link type="primary" @click="readOne(n)">标已读</el-button>
          </div>
          <div class="notif-content">{{ n.content }}</div>
          <div class="notif-time">{{ fmt(n.createdAt) }}</div>
        </div>
        <el-empty v-if="!notifs.length" description="暂无通知" :image-size="60" />
      </el-scrollbar>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Odometer, Folder, Box, Files, Position, Monitor, User, CaretBottom, Bell, Grid, Histogram, Key, Fold, Expand,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listNotifications, unreadCount, markRead, markAllRead, getProject, getApplication } from '../api'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const groups = [
  { title: '工作台', items: [
    { index: '/workbench', title: '我的工作台', icon: Grid },
    { index: '/dashboard', title: '总览', icon: Odometer },
  ]},
  { title: '项目协作', items: [
    { index: '/projects', title: '项目管理', icon: Folder },
  ]},
  { title: '持续交付', items: [
    { index: '/applications', title: '应用管理', icon: Box },
    { index: '/artifacts', title: '制品库', icon: Files },
    { index: '/deployments', title: '部署管理', icon: Position },
    { index: '/environments', title: '环境管理', icon: Monitor },
  ]},
  { title: '效能', items: [
    { index: '/metrics', title: '效能度量', icon: Histogram },
  ]},
  { title: '系统', items: [
    { index: '/tokens', title: '个人令牌', icon: Key },
    { index: '/admin/users', title: '用户管理', icon: User, admin: true },
  ]},
]

const unread = ref(0)
const notifs = ref([])
const notifDialog = ref(false)
let notifTimer = null
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

async function loadUnread() {
  try { unread.value = (await unreadCount()).data.count } catch {}
}
async function openNotif() {
  notifDialog.value = true
  notifs.value = (await listNotifications()).data
}
async function readOne(n) {
  await markRead(n.id)
  n.readAt = new Date().toISOString()
  loadUnread()
}
async function readAll() {
  await markAllRead()
  notifs.value.forEach((n) => (n.readAt = new Date().toISOString()))
  loadUnread()
}
async function openNotifLink(n) {
  if (!n.readAt) { markRead(n.id).then(loadUnread) }
  if (n.link) { notifDialog.value = false; router.push(n.link) }
}

const activeMenu = computed(() => {
  const seg = route.path.split('/')[1] || 'dashboard'
  if (seg === 'runs') return '/applications'
  return '/' + seg
})

// 真实名称面包屑：项目名 / 应用名
const projName = ref('')
const appName = ref('')
watch(() => route.fullPath, async () => {
  const seg = route.path.split('/')[1]
  const id = route.params.id
  projName.value = ''
  appName.value = ''
  if (seg === 'projects' && id) { try { projName.value = (await getProject(id)).data.name } catch {} }
  if ((seg === 'applications' || seg === 'runs') && id) {
    try { appName.value = (await getApplication(id)).data.name } catch {}
  }
}, { immediate: true })

const crumbs = computed(() => {
  const seg = route.path.split('/')[1]
  switch (seg) {
    case 'workbench': return [{ label: '我的工作台' }]
    case 'dashboard': return [{ label: '总览' }]
    case 'projects':
      if (!route.params.id) return [{ label: '项目管理' }]
      return projName.value ? [{ label: '项目管理', to: '/projects' }, { label: projName.value }] : [{ label: '项目管理', to: '/projects' }, { label: '项目详情' }]
    case 'applications':
      return appName.value ? [{ label: '应用管理', to: '/applications' }, { label: appName.value }] : [{ label: '应用管理' }]
    case 'runs': return [{ label: '应用管理', to: '/applications' }, { label: '构建详情' }]
    case 'artifacts': return [{ label: '制品库' }]
    case 'deployments': return [{ label: '部署管理' }]
    case 'environments': return [{ label: '环境管理' }]
    case 'metrics': return [{ label: '效能度量' }]
    case 'tokens': return [{ label: '个人令牌' }]
    case 'admin': return [{ label: '系统管理' }, { label: '用户管理' }]
    default: return [{ label: 'DevFusion' }]
  }
})

onMounted(() => {
  if (!auth.user) auth.fetchMe().catch(() => {})
  loadUnread()
  notifTimer = setInterval(loadUnread, 30000)
})
onBeforeUnmount(() => { if (notifTimer) clearInterval(notifTimer) })
</script>

<style scoped>
.layout { height: 100vh; }
.sidebar {
  background: var(--df-sidebar-bg);
  border-right: 1px solid var(--df-border-light);
  display: flex; flex-direction: column;
  transition: width .18s;
  overflow: hidden;
}
.brand {
  height: var(--df-header-height);
  display: flex; align-items: center; gap: 8px;
  padding: 0 16px; border-bottom: 1px solid var(--df-border-light); flex-shrink: 0;
}
.logo-mark {
  width: 26px; height: 26px; border-radius: 7px;
  background: var(--df-primary); color: #fff; font-weight: 700; font-size: 15px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.logo { font-size: 16px; font-weight: 700; color: var(--df-text); letter-spacing: .3px; white-space: nowrap; }

.side-scroll { flex: 1; overflow-y: auto; padding: 8px 10px; }
.group-title {
  font-size: 11px; color: var(--df-text-3); padding: 12px 8px 4px; letter-spacing: .5px;
}
.side-menu { border-right: none; background: transparent; }
.side-menu :deep(.el-menu-item) {
  height: 38px; line-height: 38px; border-radius: var(--df-radius-sm);
  color: var(--df-text-2); font-size: var(--df-font); margin-bottom: 2px;
}
.side-menu :deep(.el-menu-item .el-icon) { color: var(--df-text-3); }
.side-menu :deep(.el-menu-item:hover) { background: var(--df-sidebar-hover); color: var(--df-text); }
.side-menu :deep(.el-menu-item.is-active) { background: var(--df-sidebar-active); color: var(--df-primary); font-weight: 600; }
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: var(--df-primary); }

.side-foot { border-top: 1px solid var(--df-border-light); padding: 6px 10px; flex-shrink: 0; }

.header {
  background: var(--df-header-bg);
  border-bottom: 1px solid var(--df-border-light);
  display: flex; align-items: center; justify-content: space-between; padding: 0 20px;
}
.crumb { font-size: var(--df-font); }
.crumb :deep(.el-breadcrumb__inner) { color: var(--df-text-3); font-weight: 400; }
.crumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) { color: var(--df-text); font-weight: 500; }

.header-right { display: flex; align-items: center; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 6px; border-radius: var(--df-radius-sm); }
.user:hover { background: var(--df-sidebar-hover); }
.user-avatar { background: var(--df-primary); font-size: 13px; }
.user-name { font-size: var(--df-font); color: var(--df-text-2); }
.user-caret { color: var(--df-text-3); }
.bell { margin-right: 14px; display: flex; align-items: center; }
.notif-item { padding: 10px 8px; border-bottom: 1px solid var(--df-border-light); }
.notif-item.unread { background: var(--df-primary-light); }
.notif-title { font-size: var(--df-font); color: var(--df-text); }
.notif-content { font-size: var(--df-font-sm); color: var(--df-text-2); margin-top: 4px; }
.notif-time { font-size: 12px; color: var(--df-text-3); margin-top: 4px; }

.main { background: var(--df-bg); padding: 20px; }
</style>
