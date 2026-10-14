/* ============================================================
 * 全站枚举映射：状态 / 优先级 / 类型 → 中文文案 + 语义色
 * 唯一事实来源。页面只允许引用这里的 label/type，不要散落硬编码。
 * 语义色约定：成功=绿 进行中=蓝 警告/待办=橙 危险/失败=红 中性=灰
 * ============================================================ */

// ---- 运行 / 部署状态 ----
export const RUN_STATUS = {
  PENDING: { label: '排队中', type: 'info' },
  RUNNING: { label: '运行中', type: 'primary' },
  IN_PROGRESS: { label: '运行中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
  WAITING_APPROVAL: { label: '待审批', type: 'warning' },
  REJECTED: { label: '已拒绝', type: 'danger' },
}

// ---- 部署单状态 ----
export const DEPLOY_STATUS = {
  PENDING: { label: '待部署', type: 'info' },
  DEPLOYING: { label: '部署中', type: 'primary' },
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
  WAITING_APPROVAL: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
}

// ---- 测试用例执行结果 ----
export const TEST_RESULT = {
  UNEXECUTED: { label: '未执行', type: 'info' },
  PASS: { label: '通过', type: 'success' },
  FAIL: { label: '失败', type: 'danger' },
  BLOCKED: { label: '阻塞', type: 'warning' },
  SKIPPED: { label: '跳过', type: 'info' },
}

// ---- 迭代状态 ----
export const ITERATION_STATUS = {
  PLANNED: { label: '规划中', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' },
}

// ---- 发布状态 ----
export const RELEASE_STATUS = {
  PLANNED: { label: '待发布', type: 'info' },
  RELEASED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'warning' },
  CANCELED: { label: '已取消', type: 'info' },
}

// ---- 测试计划状态 ----
export const PLAN_STATUS = {
  PLANNED: { label: '规划中', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' },
}

// ---- 工作项类型 ----
export const WORK_ITEM_TYPE = {
  STORY: { label: '需求' },
  TASK: { label: '任务' },
  BUG: { label: '缺陷' },
}

// ---- 项目状态 ----
export const PROJECT_STATUS = {
  ACTIVE: { label: '进行中', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
}

// ---- 优先级 ----
export const PRIORITY = {
  URGENT: { label: '紧急', type: 'danger' },
  HIGH: { label: '高', type: 'warning' },
  MEDIUM: { label: '中', type: 'primary' },
  LOW: { label: '低', type: 'info' },
}

// ---- 工作项状态分类（后端动态状态按 category 着色） ----
export const STATUS_CATEGORY = {
  TODO: { type: 'info' },
  IN_PROGRESS: { type: 'warning' },
  DONE: { type: 'success' },
}

const TABLES = [RUN_STATUS, DEPLOY_STATUS, TEST_RESULT, ITERATION_STATUS, RELEASE_STATUS, PLAN_STATUS]

// 按业务域查表：避免通用 statusLabel 先命中 RUN_STATUS 把
// 工作项/迭代/测试计划的 IN_PROGRESS 误标为"运行中"。
export const STATUS_DOMAIN = {
  run: RUN_STATUS,
  deploy: DEPLOY_STATUS,
  testResult: TEST_RESULT,
  iteration: ITERATION_STATUS,
  release: RELEASE_STATUS,
  plan: PLAN_STATUS,
  workItem: STATUS_CATEGORY,
  project: PROJECT_STATUS,
}

// 通用：取中文文案。传 domain 精确查表；未传则全表兜底（保持向后兼容）。
export function statusLabel(code, domain) {
  if (!code) return '-'
  if (domain && STATUS_DOMAIN[domain]) return STATUS_DOMAIN[domain][code]?.label || code
  for (const t of TABLES) if (t[code]) return t[code].label
  return code
}

// 通用：取 el-tag type。
export function statusType(code, domain) {
  if (!code) return 'info'
  if (domain && STATUS_DOMAIN[domain]) return STATUS_DOMAIN[domain][code]?.type || 'info'
  for (const t of TABLES) if (t[code]) return t[code].type
  return 'info'
}

export function priorityLabel(p) {
  return PRIORITY[p]?.label || p || '-'
}
export function priorityType(p) {
  return PRIORITY[p]?.type || 'info'
}

// ---- 时间格式化：统一 YYYY-MM-DD HH:mm ----
export function fmtTime(t, withSeconds = false) {
  if (!t) return '-'
  const d = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t)
  if (isNaN(d.getTime())) return String(t).slice(0, 16)
  const p = (n) => String(n).padStart(2, '0')
  const base = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return withSeconds ? base + ':' + p(d.getSeconds()) : base
}
