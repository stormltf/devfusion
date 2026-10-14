import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('df_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (body.code === 401) {
        localStorage.removeItem('df_token')
        if (location.pathname !== '/login') location.href = '/login'
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body
  },
  (err) => {
    const msg = err.response?.data?.msg || err.message || '网络错误'
    if (err.response?.status === 401) {
      localStorage.removeItem('df_token')
      if (location.pathname !== '/login') location.href = '/login'
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  },
)

// ---------- auth ----------
export const login = (data) => http.post('/auth/login', data)
export const logout = () => http.post('/auth/logout')
export const getMe = () => http.get('/auth/me')

// ---------- users ----------
export const listUsers = (params) => http.get('/users', { params })
export const listSimpleUsers = () => http.get('/users/simple')
export const createUser = (data) => http.post('/users', data)
export const updateUser = (id, data) => http.put(`/users/${id}`, data)
export const resetUserPassword = (id, password) => http.put(`/users/${id}/password`, { password })
export const deleteUser = (id) => http.delete(`/users/${id}`)

// ---------- projects ----------
export const listProjects = (params) => http.get('/projects', { params })
export const createProject = (data) => http.post('/projects', data)
export const getProject = (id) => http.get(`/projects/${id}`)
export const updateProject = (id, data) => http.put(`/projects/${id}`, data)
export const deleteProject = (id) => http.delete(`/projects/${id}`)
export const listMembers = (pid) => http.get(`/projects/${pid}/members`)
export const addMember = (pid, data) => http.post(`/projects/${pid}/members`, data)
export const updateMember = (pid, userId, data) => http.put(`/projects/${pid}/members/${userId}`, data)
export const removeMember = (pid, userId) => http.delete(`/projects/${pid}/members/${userId}`)

// ---------- workflow ----------
export const listStatuses = (pid) => http.get(`/projects/${pid}/statuses`)
export const createStatus = (pid, data) => http.post(`/projects/${pid}/statuses`, data)
export const updateStatus = (pid, sid, data) => http.put(`/projects/${pid}/statuses/${sid}`, data)
export const deleteStatus = (pid, sid) => http.delete(`/projects/${pid}/statuses/${sid}`)

// ---------- iterations ----------
export const listIterations = (pid, params) => http.get(`/projects/${pid}/iterations`, { params })
export const createIteration = (pid, data) => http.post(`/projects/${pid}/iterations`, data)
export const getIteration = (id) => http.get(`/iterations/${id}`)
export const updateIteration = (id, data) => http.put(`/iterations/${id}`, data)
export const changeIterationStatus = (id, status) => http.put(`/iterations/${id}/status`, { status })
export const deleteIteration = (id) => http.delete(`/iterations/${id}`)

// ---------- work items ----------
export const listItems = (pid, params) => http.get(`/projects/${pid}/items`, { params })
export const createItem = (pid, data) => http.post(`/projects/${pid}/items`, data)
export const getItem = (id) => http.get(`/items/${id}`)
export const updateItem = (id, data) => http.put(`/items/${id}`, data)
export const deleteItem = (id) => http.delete(`/items/${id}`)
export const transitionItem = (id, statusCode) => http.post(`/items/${id}/transition`, { statusCode })
export const listComments = (id) => http.get(`/items/${id}/comments`)
export const addComment = (id, content) => http.post(`/items/${id}/comments`, { content })
export const listActivities = (id) => http.get(`/items/${id}/activities`)
export const getBoard = (pid, type) => http.get(`/projects/${pid}/board`, { params: { type } })

// ---------- test cases ----------
export const listTestCases = (pid, params) => http.get(`/projects/${pid}/test-cases`, { params })
export const createTestCase = (pid, data) => http.post(`/projects/${pid}/test-cases`, data)
export const updateTestCase = (id, data) => http.put(`/test-cases/${id}`, data)
export const deleteTestCase = (id) => http.delete(`/test-cases/${id}`)
export const executeTestCase = (id, result, bugItemId) =>
  http.post(`/test-cases/${id}/execute`, { result, bugItemId })
export const getTestStats = (pid) => http.get(`/projects/${pid}/test-stats`)

// ---------- applications / pipelines ----------
export const listApplications = (params) => http.get('/applications', { params })
export const createApplication = (data) => http.post('/applications', data)
export const getApplication = (id) => http.get(`/applications/${id}`)
export const updateApplication = (id, data) => http.put(`/applications/${id}`, data)
export const deleteApplication = (id) => http.delete(`/applications/${id}`)

export const listPipelines = (appId) => http.get(`/applications/${appId}/pipelines`)
export const createPipeline = (appId, data) => http.post(`/applications/${appId}/pipelines`, data)
export const getPipeline = (id) => http.get(`/pipelines/${id}`)
export const updatePipeline = (id, data) => http.put(`/pipelines/${id}`, data)
export const deletePipeline = (id) => http.delete(`/pipelines/${id}`)
export const triggerPipeline = (id, data) => http.post(`/pipelines/${id}/run`, data || {})

// ---------- pipeline variables ----------
export const listPipelineVariables = (pid) => http.get(`/pipelines/${pid}/variables`)
export const createPipelineVariable = (pid, data) => http.post(`/pipelines/${pid}/variables`, data)
export const deletePipelineVariable = (pid, id) => http.delete(`/pipelines/${pid}/variables/${id}`)

// ---------- runs ----------
export const listRuns = (params) => http.get('/runs', { params })
export const listPipelineRuns = (pid, params) => http.get(`/pipelines/${pid}/runs`, { params })
export const getRun = (id) => http.get(`/runs/${id}`)
export const cancelRun = (id) => http.post(`/runs/${id}/cancel`)
export const approveRunJob = (runId, jobId) => http.post(`/runs/${runId}/jobs/${jobId}/approve`)
export const rejectRunJob = (runId, jobId) => http.post(`/runs/${runId}/jobs/${jobId}/reject`)
export const getRunWorkItems = (runId) => http.get(`/runs/${runId}/work-items`)

// ---------- access tokens / openapi ----------
export const listTokens = () => http.get('/tokens')
export const createToken = (data) => http.post('/tokens', data)
export const deleteToken = (id) => http.delete(`/tokens/${id}`)

// ---------- notifications ----------
export const listNotifications = () => http.get('/notifications')
export const unreadCount = () => http.get('/notifications/unread-count')
export const markRead = (id) => http.post(`/notifications/${id}/read`)
export const markAllRead = () => http.post('/notifications/read-all')

// ---------- workbench / metrics ----------
export const getWorkbench = () => http.get('/workbench/me')
export const getMetrics = (days) => http.get('/dashboard/metrics', { params: { days } })

// ---------- hosts / templates ----------
export const listHostGroups = () => http.get('/host-groups')
export const listTemplates = (appId) => http.get('/deploy-templates', { params: { appId } })

// ---------- artifact promote / deploy approval ----------
export const promoteArtifact = (id) => http.post(`/artifacts/${id}/promote`)
export const approveDeployment = (id) => http.post(`/deployments/${id}/approve`)
export const rejectDeployment = (id) => http.post(`/deployments/${id}/reject`)

// ---------- environments / artifacts / deployments ----------
export const listEnvironments = () => http.get('/environments')
export const createEnvironment = (data) => http.post('/environments', data)
export const updateEnvironment = (id, data) => http.put(`/environments/${id}`, data)
export const deleteEnvironment = (id) => http.delete(`/environments/${id}`)

export const listArtifacts = (params) => http.get('/artifacts', { params })

export const listDeployments = (params) => http.get('/deployments', { params })
export const createDeployment = (data) => http.post('/deployments', data)
export const getDeployment = (id) => http.get(`/deployments/${id}`)
export const cancelDeployment = (id) => http.post(`/deployments/${id}/cancel`)
export const rollbackDeployment = (id) => http.post(`/deployments/${id}/rollback`)

// ---------- dashboard ----------
export const getGlobalDashboard = () => http.get('/dashboard/global')
export const getProjectDashboard = (pid) => http.get(`/dashboard/project/${pid}`)
export const getBugReport = (pid) => http.get(`/dashboard/project/${pid}/bug-report`)

// ---------- PM: backlog / batch / views / worklogs ----------
export const listBacklog = (pid, params) => http.get(`/projects/${pid}/backlog`, { params })
export const planBacklog = (pid, data) => http.post(`/projects/${pid}/backlog/plan`, data)
export const unplanBacklog = (pid, data) => http.post(`/projects/${pid}/backlog/unplan`, data)
export const batchUpdateItems = (pid, data) => http.post(`/projects/${pid}/items/batch-update`, data)
export const batchDeleteItems = (pid, data) => http.post(`/projects/${pid}/items/batch-delete`, data)
export const listViews = (pid, params) => http.get(`/projects/${pid}/views`, { params })
export const createView = (pid, data) => http.post(`/projects/${pid}/views`, data)
export const updateView = (id, data) => http.put(`/views/${id}`, data)
export const deleteView = (id) => http.delete(`/views/${id}`)
export const listWorklogs = (itemId) => http.get(`/items/${itemId}/worklogs`)
export const addWorklog = (itemId, data) => http.post(`/items/${itemId}/worklogs`, data)
export const deleteWorklog = (itemId, logId) => http.delete(`/items/${itemId}/worklogs/${logId}`)

// ---------- PM: iteration burndown ----------
export const getBurndown = (iterId, metric) => http.get(`/iterations/${iterId}/burndown`, { params: { metric } })

// ---------- PM: test plans ----------
export const listTestPlans = (pid) => http.get(`/projects/${pid}/test-plans`)
export const createTestPlan = (pid, data) => http.post(`/projects/${pid}/test-plans`, data)
export const getTestPlan = (id) => http.get(`/test-plans/${id}`)
export const updateTestPlan = (id, data) => http.put(`/test-plans/${id}`, data)
export const deleteTestPlan = (id) => http.delete(`/test-plans/${id}`)
export const addPlanCases = (id, data) => http.post(`/test-plans/${id}/cases`, data)
export const removePlanCase = (id, caseId) => http.delete(`/test-plans/${id}/cases/${caseId}`)
export const setPlanExecutors = (id, data) => http.put(`/test-plans/${id}/executors`, data)
export const executePlanCase = (id, data) => http.post(`/test-plans/${id}/execute`, data)
export const listCaseExecutions = (caseId) => http.get(`/test-cases/${caseId}/executions`)

// ---------- PM: releases ----------
export const listReleases = (pid) => http.get(`/projects/${pid}/releases`)
export const createRelease = (pid, data) => http.post(`/projects/${pid}/releases`, data)
export const getRelease = (id) => http.get(`/releases/${id}`)
export const updateRelease = (id, data) => http.put(`/releases/${id}`, data)
export const deleteRelease = (id) => http.delete(`/releases/${id}`)
export const changeReleaseStatus = (id, status) => http.put(`/releases/${id}/status`, { status })

export default http
