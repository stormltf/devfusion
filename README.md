# DevFusion - 项目管理 + DevOps 一体化研发平台

参照 **ONES**（项目管理）与 **简单云 ezOne**（DevOps）两个产品调研后整合实现的一体化系统。

- 技术栈：Vue 3 + Element Plus / Spring Boot 3.5 + MyBatis-Plus / MySQL 9 + Redis 8
- 定位：本地可跑通的研发管理闭环 —— 项目规划 → Backlog/迭代/看板 → 燃尽/测试计划 → 流水线（并行/审批/定时）→ 构建 → 审批部署 → 回滚

## 功能清单

**项目管理（ONES 形态）**
- 项目管理：项目 CRUD、项目标识（工作项编号前缀）、成员与角色（OWNER/ADMIN/MEMBER）
- 统一工作项：需求 / 任务 / 缺陷共用一套模型，编号自动生成（`GWP-STORY-1`）
- 可配置工作流：项目 × 类型独立状态列表（初始态/终态/归类），新建项目自动套默认模板
- 看板：列绑状态，HTML5 拖拽流转
- Backlog：待规划 / 已规划双区，拖拽进迭代；迭代卡片显示当前/推荐工作量（story point）
- 迭代：规划中 → 进行中 → 已完成状态机，完成前校验无未完成项；燃尽图（工作项数 / 故事点）
- 发布（Release）：版本列表、发布日期、状态流转
- 工时：工作项登记预估/剩余/实际，详情抽屉内置 Time 页签
- 已保存视图：私有/公共筛选器落库（`work_item_view`）
- 工作项详情：状态流转、指派、优先级、子项、评论、操作动态（全留痕）
- 测试用例：CRUD、执行标记（通过/失败/阻塞）、失败可关联缺陷、结果统计
- 测试计划：挂用例库、逐用例设执行人、批量执行、执行历史留痕
- 缺陷报表：项目维度 Bug 分析面板
- 总览看板：项目/应用/构建/部署统计、构建趋势、最近部署；我的工作台聚合

**DevOps（简单云形态）**
- 应用管理：服务档案（语言/仓库/负责人）
- 流水线：Stage × Job 两级可视化编排，Stage 内 Job 可并行；插件含人工审批、子流水线、质量门禁
- 触发：手动、Webhook（token 鉴权）、定时（ScheduleService）、OpenAPI（个人令牌）
- 变量：流水线静态变量（`pipeline_variable`，secret 在日志打码），运行快照 `run_variable`
- 构建：run 快照隔离，日志流式查看，可取消；Job 超时与审批暂停
- 制品库：构建成功自动归档，半成品/成品晋级（promote）
- 环境与主机：自建环境、主机组（host_group / host）
- 部署：部署单（应用+制品+环境）四步模拟执行，支持审批流、部署模板、可取消、可回滚
- 开放能力：个人访问令牌（Tokens 页）、工作项 ↔ 执行记录双向关联
- 平台：站内通知中心、效能度量（Metrics 页）、用户管理（仅管理员）

## 界面对照

ONES / ezOne 截图与本地页面并排对比入口：[docs/screenshots/compare.html](./docs/screenshots/compare.html)。
UI 设计令牌与改版规范：[docs/ui-guidelines.md](./docs/ui-guidelines.md)。

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17+（本机验证用 25） | Spring Boot 3.5 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 20+（本机验证用 26） | 前端与 e2e |
| MySQL | 8.0+（本机验证用 9.5） | 数据库，本机用 brew 安装 |
| Redis | 6+（本机验证用 8.4） | 会话 / 编号自增 / 取消标志 / 定时触发，本机用 brew 安装 |

## 启动步骤（首次）

```bash
# 1. 启动 MySQL 和 Redis（macOS + brew；Linux 用 systemctl）
brew services start mysql redis

# 2. 建库、建应用账号（账号密码与 backend/src/main/resources/application.yml 保持一致）
mysql -uroot -e "
CREATE DATABASE IF NOT EXISTS devfusion DEFAULT CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'devfusion'@'localhost' IDENTIFIED BY 'devfusion123';
GRANT ALL PRIVILEGES ON devfusion.* TO 'devfusion'@'localhost';
FLUSH PRIVILEGES;"

# 3. 建表（可重复执行，DROP IF EXISTS 后重建）
mysql -uroot devfusion < backend/src/main/resources/schema.sql

# 4. 启动后端（8080；user 表为空时自动写入演示数据，无需手动导入）
cd backend && mvn spring-boot:run

# 5. 另开一个终端，启动前端（5173）
cd frontend && npm install && npm run dev
```

打开 **http://localhost:5173**：

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 管理员（可见"用户管理"菜单） |
| zhangwei / lina / wangqiang | admin123 | 普通成员 |

> CORS：后端跨域来源由 `application.yml` 的 `devfusion.cors.allowed-origin` 控制，默认 `http://localhost:5173`；改前端端口时同步修改。

**建议体验路径**：工作台 → 项目管理/智能网关平台（Backlog 拖拽、列表、看板、迭代燃尽、测试计划、发布）→ 应用管理（流水线编排含并行/审批/定时，保存并触发看构建日志）→ 部署管理（新建部署单走审批 → 四步日志 → 回滚）→ 制品库（晋级）/ 主机组 / 令牌 / 效能度量。

## 日常启停

```bash
# 启动（已初始化过，跳过 2-3 步）
brew services start mysql redis
cd backend  && mvn spring-boot:run
cd frontend && npm run dev

# 停止
# 前端/后端所在终端 Ctrl+C；MySQL/Redis:
brew services stop mysql redis
```

- 端口占用时改端口：后端 `application.yml` 的 `server.port` + 前端 `vite.config.js` 的 proxy target；前端端口改 `server.port`。
- **重置演示数据**：停后端 → `mysql -uroot devfusion < backend/src/main/resources/schema.sql` → `redis-cli -n 3 flushdb` → 重启后端（自动重新写入种子数据）。
- 后端连接信息（库/账号/密码/Redis）都在 `backend/src/main/resources/application.yml` 的 `spring.datasource` 与 `spring.data.redis`。

## Webhook / OpenAPI 触发构建

流水线触发方式选"Webhook"后，用详情页展示的 token 触发（无需登录态）：

```bash
curl -X POST "http://localhost:8080/api/webhook/pipelines/{pipelineId}/trigger?token={webhookToken}"
```

个人访问令牌（PAT）走 OpenAPI（`/api/open/**` 已在拦截器放行）：

```bash
curl -X POST "http://localhost:8080/api/open/pipelines/{pipelineId}/trigger" -H "Authorization: Bearer <PAT>"
```

## e2e 验证

需要后端（8080）和前端（5173）已启动：

```bash
cd e2e && npm install && npx playwright install chromium
npx playwright test    # 82 个用例（原有 70 + 项目管理新功能 7 + DevOps 新功能 5），两轮连跑全绿
```

用例可重复执行（连续多轮全绿），测试产生的脏数据集中在标题含 `E2E-` 的记录。

## 目录结构

- [backend](./backend) — Spring Boot，22 个模块（auth/user/project/workitem/iteration/workflow/testcase/testplan/app/environment/pipeline/runner/artifact/deploy/host/token/notification/workbench/release 等）
- [frontend](./frontend) — Vue 3 单页应用，页面在 `src/views`（15 个），项目内子组件在 `src/components`（11 个）
- [e2e](./e2e) — Playwright 用例，`npx playwright show-trace test-results/.../trace.zip` 可回放失败过程
- [docs](./docs) — 调研报告（ONES / 简单云）、系统设计、[功能缺口清单](./docs/gap-analysis.md)、[UI 规范](./docs/ui-guidelines.md)

设计取舍与非目标见 [docs/design.md](./docs/design.md)，逐条落地状态见 [docs/gap-analysis.md](./docs/gap-analysis.md)。
