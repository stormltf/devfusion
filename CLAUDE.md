# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

DevFusion：参照 ONES（项目管理）+ 简单云 ezOne（DevOps）调研后实现的一体化研发管理平台。前后端分离，本地单机可跑通完整研发闭环。

- `backend/` — Spring Boot 3.5 + MyBatis-Plus 3.5 + MySQL + Redis，Java 17+ 编译（本机 JDK 25）
- `frontend/` — Vue 3 + Vite + Element Plus + Pinia，端口 5173，`/api` 代理到 8080
- `e2e/` — Playwright，82 个用例，可重复执行
- `docs/` — ONES/简单云调研报告、系统设计、[gap-analysis.md](./docs/gap-analysis.md)（逐条落地状态）、[ui-guidelines.md](./docs/ui-guidelines.md)（新增页面必须遵循）

## 常用命令

```bash
# 启动（依赖 MySQL/Redis 在跑，首次初始化见 README）
cd backend  && mvn spring-boot:run
cd frontend && npm run dev

# 全量 e2e（需前后端已启动；workers=1 串行，~2 分钟）
cd e2e && npx playwright test
npx playwright show-trace test-results/<dir>/trace.zip   # 失败回放

# 重置演示数据（停后端后执行，重启自动重新 seed）
mysql -uroot devfusion < backend/src/main/resources/schema.sql
redis-cli -n 3 flushdb
```

## 架构与关键约定

### 后端

- **统一响应包装**：所有接口返回 `Result<T>`（`{code:0, msg, data}`）；错误走 `ApiException` + `GlobalExceptionHandler`。新接口漏包 `Result` 会导致前端 `.data` 读到 undefined（历史上 `/users/simple` 裸数组返回曾炸掉成员对话框的渲染）。
- **鉴权**：JWT（jti）+ Redis 会话双控。登录后写 `df:session:{jti}`，拦截器校验签名且会话存在，登出删 key。WebConfig 放行：`/api/auth/login`、`/api/webhook/**`、`/api/open/**`（PAT 触发，由 `Authorization: Bearer <token>` 校验 access_token 表）。
- **MyBatis-Plus 约定**：逻辑删字段 `deleted`（@TableLogic）、`createdAt/updatedAt` 由 `MyMetaObjectHandler` 填充。**MySQL 保留字必须在 @TableName/@TableField 加反引号**：现有 `key`、`user`、`parallel`（PipelineJob/RunJob 实体上 `@TableField("`parallel`")`）。
- **updateById 不置空**：MyBatis-Plus `updateById` 忽略 null 字段，需要把字段清空时必须用 `Wrappers.<T>lambdaUpdate().set(Entity::getX, null)`（参考 BacklogController）。
- **编号生成**：工作项/用例编号一律走 Redis `INCR df:seq:{projectId}:{type}`。**种子数据（DataSeeder）也必须走同一计数器**，否则业务侧首个编号撞唯一键。
- **流水线执行引擎**（`runner/PipelineExecutor`）：触发时把 stage/job 复制为 run 快照（`run_stage`/`run_job`），编辑流水线不影响进行中的 run；`RunService.trigger` 用 `TransactionSynchronization.afterCommit` 提交执行（直接在事务内提交会读不到未提交的 run，卡 PENDING）；取消标志在 Redis `df:cancel:run:{id}`，**取消优先于失败**（终态必须是 CANCELED 而非 FAILED）。Stage 内 Job 按 `parallel=1` 并行；审批 Job 终态前为 `WAITING_APPROVAL`，走 `/runs/{id}/jobs/{jobId}/approve|reject`。
- **部署审批**：部署单审批前状态为 `PENDING_APPROVAL`，approve/reject 接口在 DeploymentController；reject 后单不进 DeployExecutor。
- **模拟执行**：构建命令不真实执行，日志由 `JobLogSimulator` 按插件类型生成。失败约定：SHELL 脚本含 `exit 1`，或 config 含 `"fail": true`（UNIT_TEST 支持 `failJobs`）。**变量 `secret=true` 的值在日志中打码为 `********`**，改 simulator 时不得回退为明文。e2e 的成败场景依赖这套约定，改 simulator 前先看 e2e 断言（如 `Failures: 3`、`BUILD SUCCESS`）。
- **禁止 `Map.of()` 返回的不可变 Map 对可能为 null 的 key 调用 `get(null)`**——直接抛 NPE（未指派工作项详情曾因此 500）。
- 部署执行器（`deploy/DeployExecutor`）同理：四步模拟 + Redis 取消标志，回滚 = 对旧制品发 `deploy_type=ROLLBACK` 的新部署单。

### 前端

- axios 拦截器统一弹错误 toast，`code===401` 清 token 跳登录页。
- 项目详情的 tab 与子路由双向同步（`ProjectDetail.vue`），新增 tab 要同时改 `TABS` 数组和 `router.js` 子路由。
- el-select 的下拉 popper 持久驻留 DOM，跨下拉的文本选择会 strict 冲突——e2e 用 `pickOption`（按 `aria-controls` 定位展开的下拉）。
- **新页面/新组件只引用 `frontend/src/styles/tokens.css` 的设计令牌**，禁止散落硬编码色值；规范全文见 [docs/ui-guidelines.md](./docs/ui-guidelines.md)。

### e2e 约定（改测试前先读 `e2e/tests/helpers.js`）

- `loginAs` 同时注入 localStorage（页面请求）和模块级 token（`api(page, method, url, ...)` 带 Authorization）；**直接用 `page.request` 不带 token 会 401**。
- `workers: 1` 串行执行，同 spec 内 test 有数据依赖（describe.serial）。
- 测试创建的业务对象标题/标识必须含唯一后缀（`uniqueName` / `Date.now`），否则跨轮残留数据导致 strict 冲突或唯一键冲突。
- 断言持久化结果（列表文本 / API 轮询）优于断言 toast——toast 有 3s 生命周期，时序竞争易 flaky。

## 已知环境坑

- JDK 23+ 默认禁用注解处理：pom 的 maven-compiler-plugin 已配 `<proc>full</proc>` + lombok annotationProcessorPaths，别删。
- MySQL 9 + Spring Boot 3.5 用 parent 管理的 mysql-connector-j 即可；分页插件需要额外引 `mybatis-plus-jsqlparser`（已在 pom）。
- CORS 来源由 `application.yml` 的 `devfusion.cors.allowed-origin` 控制（默认 `http://localhost:5173`），改前端端口要同步改。
