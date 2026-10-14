# 功能缺口清单（Gap Analysis）

> 统计时间：2026-10-14。对照两份调研报告（[research-ones.md](./research-ones.md)、[research-simplecloud.md](./research-simplecloud.md)）逐条核对代码实际状态。

**首段结论**：合并 ONES 侧 26 条与 ezOne 侧 22 条共 **48 条**，其中 **已实现 32 条、部分实现 1 条、未实现 15 条**；未实现条目全部落在 P2，原因统一为依赖外部系统或成本过高，已列入非目标。P0 共 11 条（ONES 6 + ezOne 5）全部落地。

## 状态图例

- **已实现**：代码中可验证（表/接口/页面对应位置已在表中标注）。
- **部分**：主干已做，子项缺失（逐条注明）。
- **未实现**：列入非目标，原因见末列。

## 一、ONES（项目管理）侧 26 条

| 编号 | 功能 | 来源 URL | 优先级 | 状态 | 对应模块 / 表 / 页面 | 未实现原因 |
|---|---|---|---|---|---|---|
| PM-01 | 迭代燃尽图（工作项数/故事点） | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/burndown-chart/ | P0 | 已实现 | `GET /api/iterations/{id}/burndown?metric=`（IterationController）；前端 IterationPanel | — |
| PM-02 | Backlog 待规划/已规划双区拖拽 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/backlog-component/ | P0 | 已实现 | BacklogController（`/backlog`、`/backlog/plan`、`/backlog/unplan`）；BacklogPanel.vue | — |
| PM-03 | 迭代概览 Sprint info 卡片 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/ | P0 | 已实现 | IterationPanel.vue 概览区 | — |
| PM-04 | 测试计划实体 | https://docs.ones.com/zh-Hans/ones-testcase/test-plan-and-execution | P0 | 已实现 | `test_plan` 表；TestPlanController；TestPlanPanel.vue | — |
| PM-05 | 测试执行历史（多轮留痕） | 同上 | P0 | 已实现 | `test_plan_case` 表；`/api/test-plans/{id}/executions` | — |
| PM-06 | 计划内逐用例设执行人 | 同上 | P0 | 已实现 | `/api/test-plans/{id}/executors` | — |
| PM-07 | 迭代工作量评估（当前/推荐 story point） | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/backlog-component/ | P1 | 已实现 | BacklogController 聚合 `sum(story_point)` | — |
| PM-08 | 已保存视图（私有/公共筛选器） | https://docs.ones.com/zh-Hans/ones-project/components/issue-type-component/ | P1 | 已实现 | `work_item_view` 表；WorkItemViewController（`/api/views`） | — |
| PM-09 | 列表排序/分组/列显隐 | 同上 | P1 | 部分 | 排序已落地（WorkItemList 可调）；**分组与列显隐未做** | — |
| PM-10 | 批量操作（改字段/删除） | 同上 | P1 | 已实现 | BatchOpController（`/items/batch-update`、`/batch-delete`） | — |
| PM-11 | 工时登记（预估/剩余/实际） | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/burndown-chart/ | P1 | 已实现 | `worklog` 表；WorklogController（`/api/items/{id}/worklogs`） | — |
| PM-12 | 发布（Release）实体 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/backlog-component/ | P1 | 已实现 | `release` 表；ReleaseController；ReleasePanel.vue | — |
| PM-13 | 详情页签（子项/工时） | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/burndown-chart/ | P1 | 已实现 | WorkItemDrawer.vue 页签 | — |
| PM-14 | 缺陷分析报表 | https://docs.ones.com/zh-Hans/ones-project/components/other-basic-functional-components/report-component | P1 | 已实现 | `GET /api/dashboard/project/{id}/bug-report`；BugReportPanel.vue | — |
| PM-15 | 迭代阶段时间轴 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/ | P2 | 已实现 | IterationPanel.vue 阶段条 | — |
| PM-16 | 需求-用例-Bug 追溯矩阵 | https://docs.ones.com/zh-Hans/ones-testcase/test-plan-and-execution | P2 | 未实现 | — | 矩阵视图成本高，非闭环必需 |
| PM-17 | 用例模块树（多级） | 同上 | P2 | 未实现 | — | `test_case.module` 现为自由文本，改树需新增模块表 |
| PM-18 | 甘特图 / 时间视图 | https://docs.ones.com/ones-project/components/components-for-waterfall-project-planning/ | P2 | 未实现 | — | 非目标（design.md 已列） |
| PM-19 | 里程碑菱形视图 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-waterfall-project-planning/gantt-component/milestone-management/ | P2 | 未实现 | — | 非目标 |
| PM-20 | Wiki | 组件库实证（文档/文件组件） | P2 | 未实现 | — | 非目标 |
| PM-21 | Excel 导入导出 | https://docs.ones.com/zh-Hans/ones-project/components/issue-type-component/ | P2 | 未实现 | — | 依赖解析库与模板维护，成本高 |
| PM-22 | 自定义字段（6-8 种） | https://docs.ones.com/zh-Hans/ones-project/issues/issues-field-types/ | P2 | 未实现 | — | 非目标（需字段表+值表双改造） |
| PM-23 | 站内通知铃铛 | 顶栏实证截图 | P2 | 已实现 | `notification` 表；NotificationController（`/unread-count`、`/read`、`/read-all`） | — |
| PM-24 | 组件库式自定义导航 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/ | P2 | 未实现 | — | UI 改版已参考其布局，不实现拖拽配置 |
| PM-25 | 附件 / Watcher / Link 关联 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/sprint-component/burndown-chart/ | P2 | 未实现 | — | 非目标 |
| PM-26 | 组件级细粒度权限矩阵 | https://docs.ones.com/zh-Hans/ones-project/components/components-for-scrum-development/backlog-component/ | P2 | 未实现 | — | 现有项目角色三级（OWNER/ADMIN/MEMBER）够用 |

## 二、ezOne（DevOps）侧 22 条

| 编号 | 功能 | 来源 URL | 优先级 | 状态 | 对应模块 / 表 / 页面 | 未实现原因 |
|---|---|---|---|---|---|---|
| DEV-01 | 流水线参数化触发 | https://help.ezone.work/guide/pipeline/buildsManual.html | P0 | 已实现 | 运行参数注入触发流程 | — |
| DEV-02 | 人工审批 Job | 同上 | P0 | 已实现 | `run_job.status=WAITING_APPROVAL`；`/api/runs/{id}/jobs/{jobId}/approve`、`/reject` | — |
| DEV-03 | 部署单审批 | https://help.ezone.work/guide/pipeline/faq.html | P0 | 已实现 | `deployment.status=PENDING_APPROVAL`；`/deployments/{id}/approve`、`/reject` | — |
| DEV-04 | Stage 内 Job 并行 | https://help.ezone.work/guide/pipeline/overview.html | P0 | 已实现 | `pipeline_job.parallel`（保留字，反引号）；PipelineExecutor 分支 | — |
| DEV-05 | 质量门禁阈值 | https://help.ezone.work/guide/pipeline/buildsManual.html | P0 | 已实现 | Job 配置内门禁阈值 | — |
| DEV-06 | 个人访问令牌 + OpenAPI 触发 | https://help.ezone.work/guide/base/personalSetting.html | P1 | 已实现 | `access_token` 表；TokenController；`/api/open/pipelines/{id}/trigger`（WebConfig 放行 `/api/open/**`）；Tokens.vue | — |
| DEV-07 | 定时触发 | https://help.ezone.work/guide/pipeline/overview.html | P1 | 已实现 | ScheduleService | — |
| DEV-08 | 半成品/成品晋级 | https://help.ezone.work/guide/package/quickStart.html 、 https://help.ezone.work/guide/package/term.html | P1 | 已实现 | artifact 晋级接口（`/{id}/promote`） | — |
| DEV-09 | 我的工作台 | https://help.ezone.work/guide/workbench/manual.html | P1 | 已实现 | WorkbenchController；Workbench.vue | — |
| DEV-10 | 工作项 ↔ 执行记录关联 | https://help.ezone.work/guide/pipeline/buildsManual.html | P1 | 已实现 | `run_work_item` 表；`/api/runs/{id}/work-items`、`/api/work-items/{itemId}/runs` | — |
| DEV-11 | 系统变量 + 静态变量 | 同上 | P1 | 已实现 | `pipeline_variable`、`run_variable` 表；JobLogSimulator 对 secret 打码 | — |
| DEV-12 | 部署模板 | https://help.ezone.work/guide/deploy/quickStart.html | P1 | 已实现 | `deploy_template` 表；DeployTemplateController | — |
| DEV-13 | 主机组 | 同上 | P1 | 已实现 | `host_group`、`host` 表；HostController（`/{id}/hosts`） | — |
| DEV-14 | 效能度量 | https://ezone.work/products/insight | P1 | 已实现 | `GET /api/dashboard/metrics`；Metrics.vue | — |
| DEV-15 | 站内通知 | https://help.ezone.work/guide/pipeline/overview.html | P1 | 已实现 | `notification` 表；NotificationService（与 PM-23 同一实现） | — |
| DEV-16 | Job 超时 | 同上 | P1 | 已实现 | `run_job.status=TIMEOUT` | — |
| DEV-17 | 子流水线触发 | https://help.ezone.work/guide/pipeline/buildsManual.html | P1 | 已实现 | 子流水线触发插件 | — |
| DEV-18 | 真实代码事件触发（Git 推送/MR） | 同上 | P2 | 未实现 | — | 依赖 git server，非目标 |
| DEV-19 | Agent / SSH 真实部署 | https://help.ezone.work/guide/deploy/quickStart.html | P2 | 未实现 | — | 依赖外部 Agent，当前为模拟执行 |
| DEV-20 | K8S / Helm 部署 | 同上 | P2 | 未实现 | — | 依赖集群，非目标 |
| DEV-21 | IM / 邮件推送 | https://help.ezone.work/guide/pipeline/overview.html | P2 | 未实现 | — | 依赖外部通道，站内通知已替代 |
| DEV-22 | SonarQube 质量门禁 | https://help.ezone.work/guide/pipeline/buildsManual.html | P2 | 未实现 | — | 依赖外部代码扫描服务 |

## 三、未实现条目汇总（15 条，均 P2）

| 类别 | 编号 | 共性原因 |
|---|---|---|
| 外部系统依赖 | DEV-18、DEV-19、DEV-20、DEV-21、DEV-22、PM-20 | 需要 git server / Agent / 集群 / IM 通道 / SonarQube / 文档服务，超出本地单机范围 |
| 建造成本过高 | PM-16、PM-17、PM-18、PM-19、PM-21、PM-22、PM-24、PM-25、PM-26 | 视图/改造工作量大且非研发闭环必需 |
