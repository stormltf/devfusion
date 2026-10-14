<template>
  <el-container class="layout">
    <el-aside class="sidebar" :width="`var(--df-sidebar-width)`">
      <div class="brand">
        <span class="logo-mark">D</span>
        <div class="logo">DevFusion</div>
      </div>
      <el-menu :default-active="activeMenu" router class="side-menu">
        <el-menu-item v-for="m in menus" :key="m.index" :index="m.index" v-show="!m.admin || auth.isAdmin">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
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
              <span class="user-name">{{ auth.nickname }}</span>
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
        <div v-for="n in notifs" :key="n.id" class="notif-item" :class="{ unread: !n.readAt }" :data-testid="`notif-${n.id}`">
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
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  Odometer, Folder, Box, Files, Position, Monitor, User, CaretBottom, Bell, Grid, Histogram, Key,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { listNotifications, unreadCount, markRead, markAllRead } from '../api'

const auth = useAuthStore()
const route = useRoute()

const menus = [
  { index: '/workbench', title: '我的工作台', icon: Grid },
  { index: '/dashboard', title: '总览', icon: Odometer },
  { index: '/projects', title: '项目管理', icon: Folder },
  { index: '/applications', title: '应用管理', icon: Box },
  { index: '/artifacts', title: '制品库', icon: Files },
  { index: '/deployments', title: '部署管理', icon: Position },
  { index: '/environments', title: '环境管理', icon: Monitor },
  { index: '/metrics', title: '效能度量', icon: Histogram },
  { index: '/tokens', title: '个人令牌', icon: Key },
  { index: '/admin/users', title: '用户管理', icon: User, admin: true },
]

const unread = ref(0)
const notifs = ref([])
const notifDialog = ref(false)
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

const activeMenu = computed(() => {
  const seg = route.path.split('/')[1] || 'dashboard'
  if (seg === 'runs') return '/applications'
  return '/' + seg
})

const crumbs = computed(() => {
  const seg = route.path.split('/')[1]
  const map = {
    dashboard: ['总览'],
    projects: route.name && route.name !== 'story' ? ['项目管理', route.meta.title || '项目详情'] : ['项目管理'],
    applications: route.meta?.title && route.meta.title !== '应用' ? ['应用管理', route.meta.title] : ['应用管理'],
    artifacts: ['制品库'],
    deployments: ['部署管理'],
    environments: ['环境管理'],
    runs: ['应用管理', '构建详情'],
    admin: ['系统管理', '用户管理'],
  }
  return (map[seg] || ['DevFusion']).map((label, i, arr) => ({ label, to: i === arr.length - 1 ? undefined : '/' + (seg === 'admin' ? 'admin/users' : seg) }))
})

onMounted(() => {
  if (!auth.user) auth.fetchMe().catch(() => {})
  loadUnread()
  setInterval(loadUnread, 30000)
})
</script>

<style scoped>
.layout { height: 100vh; }

.sidebar {
  background: var(--df-sidebar-bg);
  border-right: 1px solid var(--df-border-light);
  display: flex;
  flex-direction: column;
}
.brand {
  height: var(--df-header-height);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  border-bottom: 1px solid var(--df-border-light);
}
.logo-mark {
  width: 26px; height: 26px; border-radius: 7px;
  background: var(--df-primary);
  color: #fff; font-weight: 700; font-size: 15px;
  display: flex; align-items: center; justify-content: center;
}
.logo { font-size: 16px; font-weight: 700; color: var(--df-text); letter-spacing: .3px; }

.side-menu { border-right: none; padding: 10px 10px; background: transparent; }
.side-menu :deep(.el-menu-item) {
  height: 40px; line-height: 40px; margin-bottom: 4px;
  border-radius: var(--df-radius-sm);
  color: var(--df-text-2); font-size: var(--df-font);
}
.side-menu :deep(.el-menu-item .el-icon) { color: var(--df-text-3); }
.side-menu :deep(.el-menu-item:hover) { background: var(--df-sidebar-hover); color: var(--df-text); }
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--df-sidebar-active);
  color: var(--df-primary); font-weight: 600;
}
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: var(--df-primary); }

.header {
  background: var(--df-header-bg);
  border-bottom: 1px solid var(--df-border-light);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px;
}
.cumb, .crumb { font-size: var(--df-font); }
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
