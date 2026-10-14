# DevFusion UI 设计规范（v1）

视觉基准：ONES / 简单云 ezOne 风格企业级 SaaS。后续新增功能页面必须遵循本规范，只引用设计令牌，不要散落硬编码色值。

## 1. 设计令牌

令牌定义在 `frontend/src/styles/tokens.css`，Element Plus 主题变量覆盖在 `frontend/src/styles/element.css`（已在 `main.js` 引入）。

### 色板

| 用途 | 变量 | 值 |
|---|---|---|
| 主色（品牌/主按钮/链接/选中） | `--df-primary` | `#0064ff` |
| 主色 hover / active | `--df-primary-hover` / `--df-primary-active` | `#3385ff` / `#0052cc` |
| 选中项浅蓝底 | `--df-primary-light` | `#e8f0ff` |
| 成功 / 完成 / 通过 | `--df-success` | `#00b42a` |
| 警告 / 进行中 / 高优 | `--df-warning` | `#ff7d00` |
| 危险 / 失败 / 删除 | `--df-danger` | `#f53f3f` |
| 信息 / 中性 | `--df-info` | `#86909c` |
| 一级文本 | `--df-text` | `#1d2129` |
| 二级文本 | `--df-text-2` | `#4e5969` |
| 三级文本（辅助） | `--df-text-3` | `#86909c` |
| 内容区底色 | `--df-bg` | `#f3f5f9` |
| 卡片/容器 | `--df-surface` | `#ffffff` |
| 细边框 | `--df-border` / `--df-border-light` | `#e5e6eb` / `#f0f1f3` |

> 禁止大面积使用紫色 / 靛蓝。主色统一为 ONES 蓝。

### 字号 / 间距

- 正文 `13px`（`--df-font`），辅助 `12px`（`--df-font-sm`），小节标题 `14px`（`--df-font-lg`）。
- 圆角：卡片 `8px`（`--df-radius`），小组件 `6px`（`--df-radius-sm`）。
- 卡片内边距 `16px`，卡片间距 `16px`（`--df-gap`）。

## 2. 布局骨架

- 全局布局见 `layout/MainLayout.vue`：**左侧浅色导航**（白底，宽 `220px`）+ **顶栏**（面包屑 + 用户菜单，高 `52px`）+ 内容区（`--df-bg` 底色，`20px` 内边距）。
- 左侧导航项选中态：浅蓝底 `--df-primary-light` + 蓝色字 `--df-primary` + 圆角。
- 内容页统一用 `<div class="page-card">` 作为卡片容器。

## 3. 组件用法

- **表格**：默认紧凑，表头浅灰底 `#fafbfc`、三级文本色；行 hover 浅蓝。禁止改字号。
- **状态 / 优先级标签**：统一用 `el-tag size="small"`，语义色映射——
  - 成功/完成/启用/通过 → `success`；失败/删除/危险 → `danger`；进行中/警告/高优/回滚 → `warning`；排队/中性/未执行 → `info`；普通主操作 → `primary`。
  - 优先级：紧急 `danger`、高 `warning`、中 `primary`、低 `info`。
- **按钮**：主操作用 `type="primary"`；表格行内操作用 `link`；危险操作用 `type="danger" link`。尺寸默认，小行内用 `size="small"`。
- **表单 / 对话框**：对话框圆角 `10px`，头部带底部分割线；表单项 label 用二级文本色。
- **抽屉**：工作项详情用 `el-drawer`，头部带分割线。
- **空状态 / 加载态**：表格用 `v-loading`；空列显示“拖到这里”等中性提示，用三级文本色。

## 4. 新页面模板写法

```vue
<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索" class="w-240" clearable />
      <el-button type="primary" data-testid="xxx-create">新建</el-button>
    </div>
    <div class="page-card">
      <el-table :data="rows"> ... </el-table>
    </div>
  </div>
</template>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }
.w-240 { width: 240px; }
</style>
```

## 5. e2e 约束（改 UI 必读）

- 所有交互元素必须保留 `data-testid`；新增按钮/输入框也要加。
- 不要改动已有 `el-menu` 菜单项文案与 `el-tabs` 的 tab 文案（e2e 用 `role=menuitem` / `role=tab` 定位）。
- 不要调整对话框表单项、`el-descriptions` 内字段的顺序（e2e 按 `nth` 定位下拉框）。
- 页面级 class 契约：`.logo`（含 DevFusion）、`.stat-card`/`.stat-value`、`.project-card`、`.comment` 不得删除。
