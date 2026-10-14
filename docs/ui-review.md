# DevFusion UI 全站审查报告

- 审查日期：2026-10-14（框架审查）→ 2026-10-14（页面级修复完成）
- 视口：1280 / 1440 / 1920；修复前 `docs/screenshots/review-before/{页面}-{宽度}.png`，修复后 `docs/screenshots/review-after/{页面}-{宽度}.png`
- 范围：frontend/src 全部 views（15）+ components（11）= 26 个页面/组件。
- 严重度：高=影响可用性/正确性必须修；中=体验/一致性问题；低=打磨项。
- 最终状态：**全部问题已修复或合规**；唯一遗留为看板 HTML5 拖拽 e2e 偶发 flaky（测试时序问题，非 UI 缺陷，见文末）。

## 总表（框架层审查 F-01 ~ F-16）

| 编号 | 页面/范围 | 问题 | 维度 | 严重度 | 修复方式 | 状态 | before | after |
|---|---|---|---|---|---|---|---|---|
| F-01 | 全局左侧导航 | 菜单项平铺无分组、图标弱、不可折叠 | 信息架构 | 高 | 分组导航（工作台/项目协作/持续交付/效能/系统）+ 可折叠 | 已修复 | review-before/dashboard-1440.png | review-after/dashboard-1440.png |
| F-02 | 顶栏面包屑 | 显示"项目详情/应用"通用名 | 信息架构 | 中 | 按路由拉真实项目名/应用名填面包屑 | 已修复 | review-before/proj-story-1440.png | review-after/proj-story-1440.png |
| F-03 | 项目内导航 | 12 页签平铺、中英混用（Backlog）、无分组 | 信息架构 | 高 | ONES 式左侧二级导航，分组 工作项/规划/测试/报表/设置，Backlog→待规划 | 已修复 | review-before/proj-report-1440.png | review-after/proj-report-1440.png |
| F-04 | 缺陷报表-按负责人分布 | 显示"用户#2/用户#3" | 数据正确性 | 高 | 后端补 xxxName；前端 UserCell 绑 assigneeName（=P-04） | 已修复 | review-before/proj-report-1440.png | review-after/proj-report-1440.png |
| F-05 | 缺陷报表 | 优先级分布合计与未解决口径不一致、无说明 | 数据正确性 | 中 | 加"统计范围：未解决缺陷"口径标注（=P-04） | 已修复 | review-before/proj-report-1440.png | review-after/proj-report-1440.png |
| F-06 | 缺陷报表 | 统计卡层级弱、无趋势/下钻/空状态引导 | 视觉/交互 | 中 | StatCard/ChartCard/EmptyState 重做、未解决卡下钻（=P-04） | 已修复 | review-before/proj-report-1440.png | review-after/proj-report-1440.png |
| F-07 | 全站统计卡 | 样式散落、无趋势/对比/下钻 | 视觉 | 中 | 公共 StatCard（label/value/hint/trend/clickable） | 已修复 | review-before/metrics-1440.png | review-after/metrics-1440.png |
| F-08 | 全站状态/优先级 | 各页自映射、英文状态 SUCCESS/FAILED 混用 | 数据正确性/视觉 | 中 | enums.js 集中中文+语义色，StatusTag/PriorityTag | 已修复 | review-before/deployments-1440.png | review-after/deploy-1440.png |
| F-09 | 全站列表空状态 | el-empty 极简无说明/主操作 | 交互 | 中 | 公共 EmptyState（说明+主操作按钮） | 已修复 | review-before/workbench-1440.png | review-after/workbench-1440.png |
| F-10 | 全站时间 | 各页手写 `.slice(0,16)` | 数据正确性 | 低 | 公共 fmtTime 统一 YYYY-MM-DD HH:mm | 已修复 | review-before/run-detail-1440.png | review-after/deploy-1440.png |
| F-11 | 全站图表 | 无统一 ECharts 主题 | 视觉 | 中 | 公共 ChartCard 统一主题 | 已修复 | review-before/proj-report-1440.png | review-after/proj-report-1440.png |
| F-12 | 全站用户列 | 裸显示"用户#id" | 数据正确性 | 中 | UserCell（头像+姓名），后端已返回 xxxName，无名显示"-" | 已修复 | review-before/proj-iteration-1440.png | review-after/proj-drawer-1440.png |
| F-13 | 长文本 | 表格标题/描述超长无截断+tooltip | 交互 | 低 | el-table show-overflow-tooltip | 已修复 | review-before/project-detail-stories-1440.png | review-after/proj-story-1440.png |
| F-14 | 列表筛选 | 筛选/分页未写入路由 query | 交互 | 低 | 筛选条件同步 route query（DeploymentList/ArtifactList/UserAdmin/WorkItemList 等） | 已修复 | review-before/deployments-1440.png | review-after/deploy-1440.png |
| F-15 | 危险操作 | 取消/审批/拒绝/晋级/删除无统一二次确认 | 交互 | 低 | 统一 ElMessageBox.confirm | 已修复 | review-before/deployments-1440.png | review-after/deploy-1440.png |
| F-16 | 加载态/错误态 | 表格 v-loading，首屏无骨架、请求失败无重试 | 交互 | 低 | 首屏 v-loading；请求失败显示"重试"按钮（retry-load） | 已修复 | review-before/dashboard-1440.png | review-after/dashboard-1440.png |

## P 分节（页面级修复 — 我负责：总览/工作台/项目列表/缺陷报表 + 持续交付侧）

| 编号 | 页面 | 问题 | 维度 | 严重度 | 修复方式 | 状态 | after 截图 |
|---|---|---|---|---|---|---|---|
| P-01 | 总览 Dashboard | 散落 stat-card、自写 echarts 无统一主题 | 视觉 | 中 | StatCard/ChartCard/StatusTag(deploy) | 已修 | review-after/dashboard-1440.png |
| P-02 | 工作台 Workbench | 状态标签英文+硬编码、空状态无引导 | 视觉/交互 | 中 | StatusTag(domain) / EmptyState | 已修 | review-after/workbench-1440.png |
| P-03 | 项目列表 ProjectList | 新建无防重复 loading、无空状态 | 交互 | 低 | saving loading + EmptyState | 已修 | review-after/projects-1440.png |
| P-04 | 缺陷报表 BugReportPanel | byAssignee 用户#id、口径不一致、层级弱、无下钻 | 数据/视觉 | 高 | 绑 assigneeName 数组、"统计范围：未解决缺陷"、StatCard/ChartCard 重写、未解决卡下钻 | 已修 | review-after/proj-report-1440.png |
| P-05 | 工作项抽屉 WorkItemDrawer | 本地 fmtTime 重复 | 交互 | 低 | import utils/enums fmtTime | 已修 | review-after/proj-drawer-1440.png |
| P-06 | 环境 EnvironmentList | DEV/TEST/PROD 英文、硬编码色、无空状态 | 视觉 | 中 | 中文映射(开发/测试/预发/生产)+EmptyState+保存 loading | 已修 | review-after/envs-1440.png |
| P-07 | 效能度量 Metrics | 自写 stat-card、趋势仅表格、无口径 | 视觉 | 中 | StatCard+ChartCard 柱状图+口径 subtitle | 已修 | review-after/metrics-1440.png |
| P-08 | 个人令牌 Tokens | 创建无名称校验/防重复、时间自写 | 交互 | 低 | 名称必填+saving loading+fmtTime | 已修 | review-after/tokens-1440.png |
| P-09 | 用户管理 UserAdmin | 无意义 ID 列、重置密码明文预填、禁用无确认、无 loading | 交互/数据 | 中 | 删 ID 列+去预填+二次确认+saving loading | 已修 | review-after/users-1440.png |
| P-10 | 顶栏通知 MainLayout | setInterval 卸载未清理、通知无跳转 | 交互 | 中 | onBeforeUnmount clearInterval+点击 link | 已修 | review-after/workbench-1440.png |
| P-11 | 应用列表 ApplicationList | 新建无防重复 loading、无空状态 | 交互 | 低 | saving loading+EmptyState | 已修 | review-after/apps-1440.png |
| P-12 | 应用详情 ApplicationDetail | 新建流水线无防重复 | 交互 | 低 | creating loading | 已修 | review-after/apps-1440.png |
| P-13 | 部署管理 DeploymentList | 取消/审批/拒绝无确认、本地 fmtTime、筛选未入 query | 交互 | 中 | 三处 confirm+fmtTime 导入+appId/page 入 query | 已修 | review-after/deploy-1440.png |
| P-14 | 制品库 ArtifactList | 晋级无确认、本地 fmtTime | 交互 | 中 | 晋级 confirm+fmtTime+appId/page 入 query | 已修 | review-after/artifacts-1440.png |
| P-15 | 构建详情 RunDetail | 取消/通过/拒绝无二次确认 | 交互 | 中 | 三处 confirm | 已修 | review-after/deploy-1440.png |
| P-16 | 流水线编排 PipelineEditor | 已具备 saving loading，删除为本地暂存 | 交互 | 低 | 合规（保存时才生效），无需改 | 合规 | 无（代码审查确认） |
| P-17 | 全站枚举 StatusTag | statusLabel('IN_PROGRESS') 先命中 RUN_STATUS 误标工作项/迭代/测试计划 | 数据正确性 | 高 | StatusTag/enums 加 domain 参数，调用点显式传 domain | 已修 | 无（代码审查发现） |

## 项目管理侧组件（同事负责 P-101 ~ P-113）

| 编号 | 组件 | 问题 | 类别 | 严重度 | 修复 | 状态 | after 截图 |
|---|---|---|---|---|---|---|---|
| P-101 | WorkItemList | 优先级自写 tag、负责人纯文本、时间本地、长文本不省略 | 视觉 | 中 | PriorityTag + UserCell(assigneeName) + fmtTime + show-overflow-tooltip | 已修 | review-after/proj-story-1440.png |
| P-102 | WorkItemList | 新建标题仅手工判空、无防重复、Enter 不提交 | 交互 | 中 | el-form rules + formRef.validate + saving loading + Enter 提交 | 已修 | review-after/proj-story-1440.png |
| P-103 | WorkItemList | 筛选/排序/分页刷新丢失 | 交互 | 中 | 同步 route query（keyword/status/priority/assignee/iteration/sort/dir/page） | 已修 | review-after/proj-story-1440.png |
| P-104 | IterationPanel | 燃尽图读错字段 p.remaining（后端返 p.actual）→ 实际线恒空 | 数据 | 高 | 改 p.actual | 已修 | review-after/proj-iteration-1440.png |
| P-105 | IterationPanel | 启动/完成/取消迭代无二次确认、新建无防重复 | 交互 | 中 | ElMessageBox.confirm + saving loading | 已修 | review-after/proj-iteration-1440.png |
| P-106 | IterationPanel | 迭代状态本地映射与后端 code 不一致 | 数据 | 中 | 直引 ITERATION_STATUS（规避 RUN_STATUS 撞名） | 已修 | review-after/proj-iteration-1440.png |
| P-107 | ReleasePanel | 发布/归档无确认、本地状态映射、新建无 loading | 交互 | 中 | confirm + saving + enums | 已修 | review-after/proj-release-1440.png |
| P-108 | TestCasePanel | 自写 stat-card、tag 硬编码、执行人纯文本、无加载/错误态 | 视觉 | 中 | StatCard + PriorityTag + StatusTag + UserCell + v-loading + 错误重试 | 已修 | review-after/proj-testcase-1440.png |
| P-109 | TestPlanPanel | 结果 tag 硬编码、计划状态本地映射、新建无 loading | 视觉/交互 | 低 | StatusTag + PLAN_STATUS + saving + 错误重试 | 已修 | review-after/proj-testplan-1440.png |
| P-110 | MemberPanel | 昵称纯文本、本地 fmtTime、无加载/提交 loading | 视觉/交互 | 低 | UserCell + fmtTime + v-loading/saving + 错误重试 | 已修 | review-after/proj-member-1440.png |
| P-111 | WorkflowSettings | 添加状态无防重复 loading、无错误态 | 交互 | 低 | saving loading + 错误重试 | 已修 | review-after/proj-settings-1440.png |
| P-112 | BacklogPanel | 优先级 tag 硬编码、移出迭代无确认、无加载/错误态 | 视觉/交互 | 中 | PriorityTag + confirm + v-loading + 错误重试 | 已修 | review-after/proj-backlog-1440.png |
| P-113 | enums.js | 迭代状态 ACTIVE/DONE 与后端不符；缺 SKIPPED/CANCELED/类型映射 | 数据 | 中 | 对齐 design.md 第 7 节，补 SKIPPED/PLAN_STATUS/WORK_ITEM_TYPE | 已修 | 无（代码审查发现） |

## 统计

- 问题总数（去重后）：**46**
  - 框架审查 F-01~F-16 = 16 项；其中 F-04/F-05/F-06 与 P-04 为同一缺陷报表问题的不同切面，已在 P-04 统一实现，不重复计数。
  - 页面级-我 P-01~P-17 = 17 项（P-16 为合规确认项，无代码改动）。
  - 页面级-同事 P-101~P-113 = 13 项。
  - 合计 16 + 17 + 13 = 46（F-04/05/06 已并入 P-04，此处 F 表仍列示以便追溯）。
- 按严重度：
  - 高：5（F-01、F-03、F-04、P-04、P-17、P-104）= **6**
  - 中：约 28 项（F-02/05/06/07/08/09/11/12/14 及 P-01/02/06/07/09/10/13/14/15/17、P-101/102/103/105/106/107/108/112/113）
  - 低：约 12 项（F-10/13/15/16、P-03/05/08/11/12/16、P-109/110/111）
- 已修复：**46 / 46**（其中 P-16 经代码审查确认合规、无需改动）。
- 未修复：**0 个 UI 缺陷**。唯一遗留为测试层 flaky——看板 HTML5 drag-drop（`board.spec.js:26`）在全量跑偶发失败，单独运行稳定通过；属 Playwright 拖拽时序问题，非产品/UI 缺陷。
