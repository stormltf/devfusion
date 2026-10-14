# DevFusion UI 设计规范（v2）

视觉基准：ONES / 简单云 ezOne 风格企业级 SaaS。新增功能页面必须遵循本规范，只引用设计令牌与公共组件，禁止散落硬编码色值/自行映射状态色。

## 1. 设计令牌

定义在 `frontend/src/styles/tokens.css`，Element Plus 覆盖在 `frontend/src/styles/element.css`。

- 主色 `--df-primary #0064ff`（hover `#3385ff` / active `#0052cc` / 浅蓝底 `#e8f0ff`）。
- 语义色：成功 `--df-success #00b42a`、警告 `--df-warning #ff7d00`、危险 `--df-danger #f53f3f`、中性 `--df-info #86909c`。**禁止大面积紫/靛蓝**。
- 文本：`--df-text #1d2129` / `--df-text-2 #4e5969` / `--df-text-3 #86909c`。
- 容器：内容底 `--df-bg #f3f5f9`、卡片 `--df-surface #fff`、细边框 `--df-border-light #f0f1f3`。
- 字号：正文 13px、辅助 12px、强调 14px；紧凑表格行高。
- 圆角：卡片 8px、小元素 6px。

## 2. 布局与导航

- **全局左侧导航**（`MainLayout.vue`）：分组渲染——工作台 / 项目协作 / 持续交付 / 效能 / 系统；当前项浅蓝底+蓝字；可折叠（底部按钮）。新增一级菜单必须挂到对应分组并加图标。
- **顶栏面包屑**：显示真实名称（如 `项目管理 / 智能网关平台`），不要写死"项目详情"。
- **项目内导航**（`ProjectDetail.vue`）：二级左侧导航，固定分组——
  - 工作项：需求 / 任务 / 缺陷
  - 规划：看板 / 待规划（原 Backlog，禁用英文）/ 迭代 / 发布
  - 测试：测试用例 / 测试计划
  - 报表：缺陷报表
  - 设置：成员 / 项目设置
  - tab↔子路由双向同步已内置；**新增项目子页先在 `ProjectDetail.vue` 的 `navGroups` 注册**，路由 path 沿用既有 `/projects/:id/<name>`。

## 3. 公共组件（frontend/src/components/common/）

| 组件 | 用途 | 关键 props |
|---|---|---|
| `PageHeader` | 页面头：标题/副标题/返回/右上操作 | `title` `subtitle` `avatarText` `onBack`；默认 slot=右上操作 |
| `StatCard` | 统计卡：数字+标签+趋势+下钻 | `label` `value` `hint` `trend`(数字) `trendUnit` `valueColor` `clickable`；`@click` |
| `EmptyState` | 空状态：说明+主操作 | `title` `description` `actionText`；`@action` |
| `UserCell` | 用户：头像+姓名（后端无名时回退"-"） | `name` `id` |
| `StatusTag` | 状态标签（中文+语义色，唯一来源 enums） | `code` **`domain`（必填，见 §4）** |
| `PriorityTag` | 优先级标签 | `value` |
| `ChartCard` | 统一 ECharts 卡（配色/网格/tooltip/图例已主题化） | `title` `subtitle` `option` `height`；`#extra` slot |

## 4. 枚举与格式化（utils/enums.js）

- 状态/优先级**只**用这里的映射，禁止页面内 if/else 拼色。
- **StatusTag 必须传 `domain`**：通用 `statusLabel(code)` 会按 code 命中第一张表，跨域时把 `IN_PROGRESS` 误标成"运行中"（本属运行域）。调用方必须显式传域：
  - `<StatusTag code="IN_PROGRESS" domain="workItem" />` → 进行中
  - 可选域：`run`(构建) / `deploy`(部署) / `testResult`(测试结果) / `iteration`(迭代) / `release`(发布) / `plan`(测试计划) / `workItem`(工作项) / `project`。
  - 例：Dashboard 部署趋势用 `domain="deploy"`；Workbench 三 tab 分别用 `workItem/run/deploy`；TestPlanPanel 用 `plan`。
- `priorityLabel/Type`：URGENT 紧急(danger) / HIGH 高(warning) / MEDIUM 中(primary) / LOW 低(info)。
- `fmtTime(t)`：统一 `YYYY-MM-DD HH:mm`，禁止 `.slice(0,16)`。
- 后端用户字段命名规则：id 字段去 Id 加 Name（assigneeName/reporterName/ownerName/creatorName/executorName/triggerUserName/operatorName/nickname）。
- 新增枚举时在 `enums.js` 登记，全站生效。

## 5. 交互规范

- **加载态**：表格用 `v-loading="loading"`；首屏请求期间整块 loading，不要白屏。
- **错误态**：请求失败不要静默空白——显示错误文案 + 一个"重试"按钮（`data-testid="retry-load"`）调用同一个 load 函数。
- 空状态：列表/图表无数据时用 `EmptyState`（写清"为什么空"+一个主操作）。
- 危险操作：删除/批量删除/取消/审批/拒绝/晋级/回滚用 `ElMessageBox.confirm` 二次确认，确认后 `ElMessage.success` 反馈。
- 表单：必填项 `el-form` rules 校验、提交按钮 `:loading="saving"` 防重复、Enter 可提交。
- 长文本：表格列 `show-overflow-tooltip`，标题单行截断+tooltip。
- **筛选/分页持久化**：列表页的筛选条件、分页页码在 `load()` 末尾 `router.replace({ query: { ...filters, page } })`；进入页面时从 `route.query` 回填（参考 DeploymentList/ArtifactList/UserAdmin/WorkItemList）。这样刷新/分享不丢筛选。
- 键盘：对话框 Enter 提交、Esc 关闭（EP 默认即可）。

## 6. 新页面模板

```vue
<template>
  <div>
    <PageHeader title="页面名" subtitle="一句话说明">
      <el-button type="primary" data-testid="xxx-create">新建</el-button>
    </PageHeader>
    <el-row :gutter="16">
      <el-col :span="8"><StatCard label="指标" :value="n" /></el-col>
    </el-row>
    <div class="page-card" style="margin-top:16px">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column label="状态"><template #default="{row}"><StatusTag :code="row.status" domain="deploy" /></template></el-table-column>
      </el-table>
      <EmptyState v-if="!loading && !rows.length" title="暂无数据" action-text="新建" @action="..." />
      <el-result v-if="error" icon="error" title="加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" data-testid="retry-load" @click="load">重试</el-button></template>
      </el-result>
    </div>
  </div>
</template>
```

## 7. 数据正确性

- 用户名一律走 `UserCell`；后端补 `xxxName` 后前端绑定，不出现"用户#id"。
- 统计卡口径必须在 `hint` 或副标题写清（如"近 7 天""未解决"），避免口径不一致。
- 全中文文案，状态/枚举禁止暴露英文原值。
