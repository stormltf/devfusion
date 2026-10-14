import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  {
    path: '/',
    component: () => import('./layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'workbench', component: () => import('./views/Workbench.vue'), meta: { title: '我的工作台' } },
      { path: 'dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '总览' } },
      { path: 'projects', component: () => import('./views/ProjectList.vue'), meta: { title: '项目' } },
      {
        path: 'projects/:id',
        component: () => import('./views/ProjectDetail.vue'),
        meta: { title: '项目详情' },
        children: [
          { path: '', redirect: { name: 'story' } },
          { path: 'story', name: 'story', component: () => import('./components/WorkItemList.vue'), props: { type: 'STORY' } },
          { path: 'task', name: 'task', component: () => import('./components/WorkItemList.vue'), props: { type: 'TASK' } },
          { path: 'bug', name: 'bug', component: () => import('./components/WorkItemList.vue'), props: { type: 'BUG' } },
          { path: 'board', name: 'board', component: () => import('./components/Board.vue') },
          { path: 'backlog', name: 'backlog', component: () => import('./components/BacklogPanel.vue') },
          { path: 'iteration', name: 'iteration', component: () => import('./components/IterationPanel.vue') },
          { path: 'testcase', name: 'testcase', component: () => import('./components/TestCasePanel.vue') },
          { path: 'testplan', name: 'testplan', component: () => import('./components/TestPlanPanel.vue') },
          { path: 'release', name: 'release', component: () => import('./components/ReleasePanel.vue') },
          { path: 'report', name: 'report', component: () => import('./components/BugReportPanel.vue') },
          { path: 'member', name: 'member', component: () => import('./components/MemberPanel.vue') },
          { path: 'settings', name: 'settings', component: () => import('./components/WorkflowSettings.vue') },
        ],
      },
      { path: 'applications', component: () => import('./views/ApplicationList.vue'), meta: { title: '应用' } },
      {
        path: 'applications/:id',
        component: () => import('./views/ApplicationDetail.vue'),
        meta: { title: '应用详情' },
      },
      {
        path: 'applications/:id/pipelines/:pipelineId',
        name: 'pipeline-editor',
        component: () => import('./views/PipelineEditor.vue'),
        meta: { title: '流水线编排' },
      },
      {
        path: 'runs/:id',
        name: 'run-detail',
        component: () => import('./views/RunDetail.vue'),
        meta: { title: '构建详情' },
      },
      { path: 'artifacts', component: () => import('./views/ArtifactList.vue'), meta: { title: '制品库' } },
      { path: 'deployments', component: () => import('./views/DeploymentList.vue'), meta: { title: '部署管理' } },
      { path: 'environments', component: () => import('./views/EnvironmentList.vue'), meta: { title: '环境管理' } },
      { path: 'metrics', component: () => import('./views/Metrics.vue'), meta: { title: '效能度量' } },
      { path: 'tokens', component: () => import('./views/Tokens.vue'), meta: { title: '个人访问令牌' } },
      { path: 'admin/users', component: () => import('./views/UserAdmin.vue'), meta: { title: '用户管理' } },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('df_token')) {
    return '/login'
  }
})

export default router
